package cn.iocoder.jtyf.module.goview.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

@TableName("t_goview_project")
@Data
public class GoviewProject implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String projectName;

    private Integer state;

    @TableField(fill = FieldFill.INSERT)
    private String createTime;

    private String createUserId;

    private Integer isDelete;

    private String indexImage;

    private String remarks;

}
