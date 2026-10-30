package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转换服务产出的可渲染资源。
 * id 使用转换服务返回的雪花编号，避免重复转换时生成第二套资源身份。
 */
@Data
@TableName("wx_dim3d_resource")
public class Dim3dConvertedResource {
    @TableId(type = IdType.INPUT)
    private Long id;
    @NotBlank private String resourceName;
    @NotBlank private String resourceType;
    @NotBlank private String assetName;
    @NotBlank private String resourceRelativePath;
    private Long dataUserId;
    private Long dataDeptId;
    private String tenantId;
    private Long createDept;
    private Long createBy;
    /** 转换服务返回的时间格式是“yyyy-MM-dd HH:mm:ss”，显式声明避免反序列化失败。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private Long updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    /** 转换资源同样采用逻辑删除，便于后续场景引用追溯。 */
    @TableLogic
    private String delFlag;
}
