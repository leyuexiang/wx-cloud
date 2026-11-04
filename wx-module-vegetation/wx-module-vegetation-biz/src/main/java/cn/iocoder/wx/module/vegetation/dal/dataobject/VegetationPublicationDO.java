package cn.iocoder.wx.module.vegetation.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 植被正式发布版本持久化对象；发布和回滚只切换数据库引用，不删除对象存储文件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("wx_veg_publication")
public class VegetationPublicationDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String sceneId;
    private String publicationVersion;
    private Long taskId;
    private String ruleVersion;
    private String generatorVersion;
    private String manifestUrl;
    private String tilesetUrl;
    private String sourceHash;
    private String status;
    private Long instanceCount;
    private Integer tileCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
