package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "测试数据表")
public class TestData {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "经度")
    private Float longitude;

    @Schema(description = "纬度")
    private Float latitude;

    @Schema(description = "位置类型")
    private String posType;
}
