package cn.iocoder.wx.framework.common.pojo;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 批量操作结果DTO
 *
 * @param <T> 操作的对象类型
 */
@Data
public class BatchOperateResult<T> {
    private List<T> successList; // 成功的对象列表
    private List<Map<T, String>> failList; // 失败的对象及原因
}
