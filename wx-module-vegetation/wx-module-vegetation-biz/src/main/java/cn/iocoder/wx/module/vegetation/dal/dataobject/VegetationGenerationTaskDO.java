package cn.iocoder.wx.module.vegetation.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 植被生成任务持久化对象。
 *
 * <p>该对象故意不继承通用 {@code BaseDO}：任务表的创建人字段是数值类型，且表结构没有
 * {@code updater} 字段。字段与 vegetation.sql 一一对应，避免 MyBatis 自动生成不存在的列。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "wx_veg_generation_task", autoResultMap = true)
public class VegetationGenerationTaskDO {

    /** 本地任务主键，同时作为发给 Python 生成器的稳定任务号。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** Python 返回的任务号；与本地数据库主键分离，兼容非数字远端任务号。 */
    private String generatorTaskId;
    private String sceneId;
    private String snapshotVersion;
    private String ruleVersion;
    private String idempotencyKey;
    private String status;
    private Integer totalTileCount;
    private Integer completedTileCount;
    private Integer failedTileCount;
    private Long candidateCount;
    private Long instanceCount;
    private String generatorVersion;
    private String publicationVersion;
    private String manifestUrl;
    private String tilesetUrl;
    /** Python按图层返回的tileset地址JSON；任务恢复后仍可向前端提供独立图层入口。 */
    private String layerTilesetUrlsJson;
    /** 三档独立入口JSON；发布与回滚恢复任务时必须保留，避免性能档退回高清入口。 */
    private String layerDensityTilesetUrlsJson;
    /** 最后确认的阶段摘要；空摘要也必须更新，防止兼容旧生成器时残留旧进度。部署前先迁移。 */
    @TableField(value = "progress_json", updateStrategy = FieldStrategy.ALWAYS)
    private String progressJson;
    private String sourceHash;
    /** 规则快照规范 JSON；以字符串保存可兼容 MySQL JSON 与测试环境 H2。 */
    @TableField(value = "snapshot_json", jdbcType = JdbcType.VARCHAR)
    private String snapshotJson;
    private String errorMessage;
    private Long creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Boolean deleted;
}
