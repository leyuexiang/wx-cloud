package cn.iocoder.wx.module.dimensions.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "材质表信息-给前端")
public class MaterialWebVo implements Serializable {

    private static final Long serialVersionUID = 1L;

    @TableId(value = "id",type = IdType.AUTO)
    private Integer id;

    @Schema(description = "名字")
    private String materialName;

    @Schema(description = "类型Id")
    private Integer typeId;

    @Schema(description = "图片相关信息")
    private String previews;

    @Schema(description = "图片地址前缀(加上文件名就可以访问)")
    private String photoAddressPrefix;

}
