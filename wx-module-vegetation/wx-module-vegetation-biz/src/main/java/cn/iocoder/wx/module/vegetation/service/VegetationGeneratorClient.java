package cn.iocoder.wx.module.vegetation.service;

import cn.iocoder.wx.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskResponse;
import cn.iocoder.wx.module.vegetation.config.VegetationGeneratorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Python生成器HTTP客户端。
 *
 * Java只负责提交任务和读取结果；候选点、株距和实例内容由Python服务执行，
 * 因此这里严格限制为短请求，不把大规模生成放进Java线程。
 */
@Component
public class VegetationGeneratorClient {

    private final RestTemplate restTemplate;
    private final VegetationGeneratorProperties properties;
    private final ObjectMapper objectMapper;

    public VegetationGeneratorClient(RestTemplate restTemplate,
                                     VegetationGeneratorProperties properties,
                                     ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public VegetationTaskResponse create(VegetationGenerationRequest request) {
        return create(request, null, request.idempotencyKey());
    }

    /**
     * 创建远端任务，并传入 Java 侧本地任务号。
     *
     * <p>本地任务先入库再调用此方法。把本地任务号作为请求字段发送，可让 Python 侧使用
     * 同一个幂等标识；即使 Python 忽略该扩展字段，Java 查询和恢复仍以本地数据库为准。</p>
     */
    public VegetationTaskResponse create(VegetationGenerationRequest request, String taskId) {
        return create(request, taskId, request.idempotencyKey());
    }

    /**
     * 创建远端任务并显式传递规范化幂等键；服务层生成的快照哈希也必须传给 Python，
     * 否则请求缺少用户幂等键时，远端无法识别重复提交。
     */
    public VegetationTaskResponse create(VegetationGenerationRequest request, String taskId,
                                          String idempotencyKey) {
        // HashMap允许幂等键为空；Python会在缺失幂等键时根据快照内容生成稳定键。
        Map<String, Object> payload = new HashMap<>();
        payload.put("snapshot", snapshotForGenerator(request));
        if (taskId != null && !taskId.isBlank()) {
            payload.put("taskId", taskId);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            payload.put("idempotencyKey", idempotencyKey);
        }
        try {
            ResponseEntity<VegetationTaskResponse> response = restTemplate.postForEntity(
                    url("/api/v1/generation-tasks"), payload, VegetationTaskResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Python生成器未返回有效任务响应");
            }
            return response.getBody();
        } catch (RestClientException exception) {
            throw new IllegalStateException("无法连接Python植被生成器", exception);
        }
    }

    /**
     * 构造Python接口专用的规则快照。
     *
     * <p>Java请求对象同时承载了数据库幂等键和规则字段，但Python的
     * GenerationSnapshot只接受规则快照本身；直接把record对象放入请求会把
     * idempotencyKey误放进snapshot，并将Java中的null覆盖Python默认值，触发
     * 严格契约校验失败。因此这里使用JSON树复制请求，移除任务级字段并递归
     * 清理null，既不修改原请求，也不影响Java侧快照哈希和审计记录。</p>
     */
    private JsonNode snapshotForGenerator(VegetationGenerationRequest request) {
        try {
            ObjectNode snapshot = objectMapper.valueToTree(request);
            snapshot.remove("idempotencyKey");
            removeNullFields(snapshot);
            return snapshot;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("无法构造Python植被规则快照", exception);
        }
    }

    /** 递归移除空对象字段，让Python模型的默认值正常生效。 */
    private void removeNullFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isNull()) {
                    fields.remove();
                } else {
                    removeNullFields(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::removeNullFields);
        }
    }

    public VegetationTaskResponse get(String taskId) {
        try {
            return restTemplate.getForObject(url("/api/v1/generation-tasks/" + taskId), VegetationTaskResponse.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("读取Python生成任务失败", exception);
        }
    }

    public VegetationTaskResponse cancel(String taskId) {
        try {
            return restTemplate.postForObject(
                    url("/api/v1/generation-tasks/" + taskId + "/cancel"), null, VegetationTaskResponse.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("取消Python生成任务失败", exception);
        }
    }

    private String url(String path) {
        return properties.getBaseUrl().replaceAll("/+$", "") + path;
    }
}
