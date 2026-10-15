package cn.iocoder.jtyf.module.vegetation.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 地块生成结果持久化对象；地块唯一键由数据库保证，重复回调可以安全覆盖同一条记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("veg_generation_tile")
public class VegetationGenerationTileDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String publicationVersion;
    private String tileId;
    private Integer tileRow;
    private Integer tileColumn;
    private String status;
    private Integer candidateCount;
    private Integer instanceCount;
    private String contentUrl;
    private String contentHash;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
