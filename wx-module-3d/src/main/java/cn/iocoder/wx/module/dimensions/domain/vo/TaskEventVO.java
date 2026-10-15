package cn.iocoder.wx.module.dimensions.domain.vo;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(autoResultMap = true)
@Schema(description = "任务事件表信息")
public class TaskEventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

//    @Schema(description = "虚拟摄像头ID")
//    private Integer virtualCameraId;

    @Schema(description = "场景ID")
    private Integer sceneId;

    @Schema(description = "外部任务ID")
    private String externalTaskId;

    @Schema(description = "外部任务名称")
    private String externalTaskName;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务Code")
    private String command;

    @Schema(description = "设备Code")
    private String deviceCode;

    @Schema(description = "任务类型：\n" +
            "Path       // 路径任务   \n" +
            "Custom // 自定义任务\n" +
            "AniTimeline  //Timeline\n" +
            "AniSystem  //动画控制器\n" +
            "NodeActive //节点显隐\n" +
            "VehicleMovement //车辆移动\n" +
            "NodeBind //节点换绑\n" +
            "AniAxisControl //轴向控制器\n" +
            "StateChange //状态变更\n" +
            "HeavyLoadHoisting //重载吊车\n" +
            "ring_holding //抱环")
    private String taskType;

    @Schema(description = "对象的唯一标识")
    private String uniqueId;

    @Schema(description = "动画片段名字")
    private String animationClipName;

    @Schema(description = "timeline资源名字")
    private String timelineAssetName;

    @Schema(description = "动画开始时间")
    private Float startTime;  // 使用包装类避免自动拆箱空指针

    @Schema(description = "动画结束时间")
    private Float endTime;    // 使用包装类避免自动拆箱空指针

    @Schema(description = "动画是否循环播放")
    private Boolean isLoop;

    @Schema(description = "任务执行时间(毫秒)")
    private Long executionTime;  // 使用Long更适合表示毫秒时间

    @Schema(description = "路径点(可选)-JSON数据结构")
    private String[] pathPoints;

    @Schema(description ="显示节点-JSON数据结构")
    private String[] activeNode;

    @Schema(description = "隐藏节点-JSON数据结构")
    private String[]  hiddenNode;

    @Schema(description = "创建者")
    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private String createUser;

    @Schema(description = "更新人")
    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updateTime;

    @Schema(description = "创建者Id")
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Integer createdBy;

    @Schema(description = "更新者Id")
    @TableField(value = "last_modified_by", fill = FieldFill.INSERT_UPDATE)
    private Integer lastModifiedBy;

    @Schema(description = "是否使用平滑运动")
    private String useSmoothMovement;
    @Schema(description = "平滑移动持续时间（秒）")
    private Float moveDuration;  // 改为Float类型

    @Schema(description = "是否使用平滑旋转")
    private String useSmoothRotation;
    @Schema(description = "旋转轴约束（锁定特定轴）")
    private String customAxisConstraint;

    @Schema(description = "解绑节点")
    private String unbindNode;

    @Schema(description = "解绑节点的父节点")
    private String unbindSubNode;

    @Schema(description = "解绑节点的目标节点")
    private String unbindTargetNode;

    @Schema(description = "状态颜色")
    private String stateColors;

    @Schema(description = "大车")
    private String mainCar;

    @Schema(description = "小车")
    private String subCar;

    @Schema(description = "吊钩")
    private String hook;

    @Schema(description = "大车起始坐标")
    private String mainCarStartPos;

    @Schema(description = "大车移动轴向")
    private String mainCarAxis;

    @Schema(description = "大车最大行程(米)")
    private Float mainCarMaxTravel;

    @Schema(description = "小车起始坐标")
    private String subCarStartPos;

    @Schema(description = "小车移动轴向")
    private String subCarAxis;

    @Schema(description = "小车最大行程(米)")
    private Float subCarMaxTravel;

    @Schema(description = "吊钩起始坐标")
    private String hookStartPos;

    @Schema(description = "吊钩移动轴向")
    private String hookAxis;

    @Schema(description = "吊钩最大升降高度(米)")
    private Float hookMaxHeight;


    @Schema(description = "坐标系")
    private Integer coordinateSystem;
    @Schema(description = "移动节点")
    private String moveNode;
    @Schema(description = "移动轴向")
    private String moveAxis;


    @Schema(description = "悬臂吊旋转节点")
    private String nodeRotation;

    @Schema(description = "悬臂吊滑块节点")
    private String nodeSlide;

    @Schema(description = "悬臂吊固定吊点节点")
    private String nodeFixedHinge;

    @Schema(description = "悬臂吊移动吊点1")
    private String nodeMoveHinge1;

    @Schema(description = "悬臂吊移动吊点2")
    private String nodeMoveHinge2;

    @Schema(description = "滑块节点移动方向")
    private String sliderDirection;

    @Schema(description = "移动吊点1移动方向")
    private String moveHinge1Direction;

    @Schema(description = "移动吊点2移动方向")
    private String moveHinge2Direction;

    @Schema(description = "虚拟摄像头")
    private Integer virtualCamera;

    @Schema(description = "AGV升降节点")
    private String liftingNode;

}