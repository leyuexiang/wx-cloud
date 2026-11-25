package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 新模型资源实体，对应 wx_3d_model 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wx_3d_model")
@Schema(description = "3D模型资源")
public class Dim3dModel extends Dim3dResourceBaseEntity {
    @NotBlank @Size(min = 2, max = 50) private String modelName;
    @Size(max = 500) private String modelDesc;
    @NotBlank private String category;
    /** 归属和计费必须由表单显式提供，缺失时交由契约校验拒绝。 */
    @NotBlank @Pattern(regexp = "SYSTEM|MINE") private String resourceBelong;
    @NotBlank @Pattern(regexp = "FREE|PAID") private String isPaid;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer price;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long coverImageId;
    /** 对外统一模型文件编号；受管目录仅在响应投影中填入资源编号，数据库仍由 resource_id 关联。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @NotNull @Positive private Long modelFileId;
    private String status = "ONLINE";

    /** 历史转换资源的真实相对路径，只读且不落库，由前端既有对象存储配置补全根地址。 */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String resourceRelativePath;

    /** 受管模型入口的临时签名地址，只在授权详情查询时生成，禁止客户端写入持久化记录。 */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String modelFileUrl;

    /** 临时地址的绝对到期时间，客户端到期后重新读取详情获取授权地址。 */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String downloadExpiresAt;

    /** 可选输入记录出现状态，编辑省略时保留旧值，显式空值用于清空；只服务于请求适配。 */
    @TableField(exist = false)
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private boolean modelDescSpecified;

    @TableField(exist = false)
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private boolean coverImageSpecified;

    public void setModelDesc(String value) {
        modelDesc = value;
        modelDescSpecified = true;
    }

    public void setCoverImageId(Long value) {
        coverImageId = value;
        coverImageSpecified = true;
    }

    /** 写入沿用整数分，响应补充精确元值，避免浮点除法引入金额误差。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getPriceYuan() {
        return price == null ? null : BigDecimal.valueOf(price, 2);
    }

    /** 目录编号与受管资源编号独立，均以字符串输出，避免浏览器大整数精度丢失。 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() { return super.getId(); }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getResourceId() { return super.getResourceId(); }
}
