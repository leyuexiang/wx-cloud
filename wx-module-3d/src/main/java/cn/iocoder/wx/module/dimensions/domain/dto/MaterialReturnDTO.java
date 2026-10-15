package cn.iocoder.wx.module.dimensions.domain.dto;

import cn.iocoder.wx.module.dimensions.entity.Glass;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
public class MaterialReturnDTO implements Serializable {
    private static final Long serialVersionUID = 1L;

    @TableId(value = "id",type = IdType.AUTO)
    private Integer id;

    @Schema(description = "名字")
    private String materialName;

    @Schema(description = "类型")
    private Integer typeId;

    @Schema(description = "图片相关信息")
    private MaterialDTO previews;

    @Schema(description = "多图片信息数组")
    private MaterialBaseDTO[] maps;

    @Schema(description = "图片地址前缀(加上文件名就可以访问)")
    private String photoAddressPrefix;

    @Schema(description = "玻璃材质信息")
    private Glass glass;

}
