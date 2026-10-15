package cn.iocoder.wx.module.dimensions.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaMessageDTO {
    private String type;
    private String version;
    private Object data;
}
