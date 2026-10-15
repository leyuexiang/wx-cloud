package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 任务消息数据
 *
 * @author wp
 */
@TableName(value = "task_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskMessageDO extends AbstractEntity<Integer>{


    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务id
     */
    private String taskId;

    /**
     * 数据版本号
     */
    private String version;

    /**
     * request/response
     */
    private String type;

    /**
     * json数据
     */
    private String data;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 业务模块
     */
    private String businessModule;

}
