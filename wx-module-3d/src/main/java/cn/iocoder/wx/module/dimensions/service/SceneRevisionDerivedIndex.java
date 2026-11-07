package cn.iocoder.wx.module.dimensions.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 同一场景修订的派生索引。
 *
 * <p>索引只从完整三维包构建，不拥有业务数据，也不接受独立修改。当前先用于保存门禁和内存校验，后续 DATA-06 再接入可重建存储。</p>
 */
public final class SceneRevisionDerivedIndex {
    public static final int INDEX_VERSION = 1;
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{64}");

    private SceneRevisionDerivedIndex() {
    }

    public record Instance(String instanceId, String parentInstanceId, int sortNo,
                           String layerKey, List<String> resourceRefKeys) {
    }

    public record Chunk(String chunkId, List<String> instanceIds) {
    }

    public record Index(long tenantId, String sceneId, int revisionNo, int indexVersion,
                        String sourceHash, List<Instance> instances, List<Chunk> chunks) {
    }

    /** 从完整三维包构建同书签索引，并在写修订前拒绝无法重建的结构。 */
    public static Index build(long tenantId, String sceneId, int revisionNo,
                              String sourceHash, JsonNode threeD) {
        if (tenantId < 1 || sceneId == null || sceneId.isBlank()) {
            throw invalid("索引身份无效");
        }
        if (revisionNo < 1 || sourceHash == null || !HASH.matcher(sourceHash).matches()) {
            throw invalid("索引书签无效");
        }
        if (threeD == null || !threeD.isObject()) {
            throw invalid("三维包必须为对象");
        }

        Map<String, String> parents = new HashMap<>();
        List<Instance> instances = new ArrayList<>();
        for (String collection : List.of("objects", "components")) {
            JsonNode nodes = threeD.path(collection);
            if (nodes.isMissingNode()) {
                continue;
            }
            if (!nodes.isArray()) {
                throw invalid("三维节点集合必须为数组");
            }
            for (JsonNode node : nodes) {
                String instanceId = text(node, "instanceId");
                String parentId = optionalText(node, "parentInstanceId");
                int sortNo = integer(node, "sortNo");
                String layerKey = optionalText(node, "layerKey");
                List<String> resourceRefKeys = textArray(node.path("resourceRefKeys"), "资源引用");
                if (parents.putIfAbsent(instanceId, parentId) != null) {
                    throw invalid("重复实例编号：" + instanceId);
                }
                instances.add(new Instance(instanceId, parentId, sortNo, layerKey, resourceRefKeys));
            }
        }
        validateParents(parents);

        List<Chunk> chunks = new ArrayList<>();
        JsonNode chunkNodes = threeD.path("streaming").path("chunks");
        if (!chunkNodes.isMissingNode()) {
            if (!chunkNodes.isArray()) {
                throw invalid("区块集合必须为数组");
            }
            for (JsonNode chunk : chunkNodes) {
                String chunkId = text(chunk, "id");
                JsonNode members = chunk.path("objectIds");
                if (!members.isArray()) {
                    throw invalid("区块成员必须为数组");
                }
                Set<String> uniqueMembers = new HashSet<>();
                List<String> instanceIds = new ArrayList<>();
                for (JsonNode member : members) {
                    if (!member.isTextual() || member.textValue().isBlank() ||
                            !uniqueMembers.add(member.textValue())) {
                        throw invalid("区块成员编号重复或无效：" + chunkId);
                    }
                    if (!parents.containsKey(member.textValue())) {
                        throw invalid("区块成员不存在：" + member.textValue());
                    }
                    instanceIds.add(member.textValue());
                }
                chunks.add(new Chunk(chunkId, List.copyOf(instanceIds)));
            }
        }
        return new Index(tenantId, sceneId, revisionNo, INDEX_VERSION, sourceHash,
                List.copyOf(instances), List.copyOf(chunks));
    }

    private static void validateParents(Map<String, String> parents) {
        Set<String> complete = new HashSet<>();
        for (String instanceId : parents.keySet()) {
            Set<String> path = new HashSet<>();
            String cursor = instanceId;
            while (cursor != null && !cursor.isEmpty() && !complete.contains(cursor)) {
                if (!path.add(cursor)) {
                    throw invalid("父子关系包含循环");
                }
                cursor = parents.get(cursor);
                if (cursor == null) {
                    throw invalid("父实例不存在");
                }
            }
            complete.addAll(path);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + "无效");
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + "无效");
        }
        return value.textValue();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw invalid(field + "必须为非负整数");
        }
        return value.intValue();
    }

    private static List<String> textArray(JsonNode values, String label) {
        if (values.isMissingNode() || values.isNull()) {
            return List.of();
        }
        if (!values.isArray()) {
            throw invalid(label + "必须为数组");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw invalid(label + "包含无效编号");
            }
            result.add(value.textValue());
        }
        return List.copyOf(result);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
