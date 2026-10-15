package cn.iocoder.jtyf.module.dimensions.domain.vo;

import lombok.Data;

@Data
public class FileInfoUpdateNameVO {
    private String fileName;
    private String newFileName;
    private Integer parentId;
    private Integer id;
}
