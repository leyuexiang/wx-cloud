package cn.iocoder.wx.module.goview.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;


@TableName("t_goview_project_data")
@Data
public class GoviewProjectData implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String projectId;

    @TableField(fill = FieldFill.INSERT)
    private String createTime;

    private String createUserId;

    private String content;

   
}
