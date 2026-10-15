package cn.iocoder.jtyf.module.dimensions.domain.vo;

import lombok.Data;

@Data
public class TaskReceiveVO {

    //版本号
    private String version;

    //request请求，response响应
    private String type;

    //数据
    private String data;
}
