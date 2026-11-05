package cn.iocoder.wx.module.system.controller.admin.dict.vo.data;

import cn.iocoder.wx.module.system.dal.dataobject.dict.DictDataDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 前端分类字典的只读响应；独立投影避免改变现有字典维护及全量精简列表的字段。 */
@Schema(description = "管理后台 - 按类型读取的业务字典项")
@Data
public class DictDataTypeRespVO {

    @Schema(description = "字典项编号，以字符串返回以保留大整数精度", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictCode;

    @Schema(description = "页面展示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictLabel;

    @Schema(description = "筛选和提交使用的原始业务编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictValue;

    @Schema(description = "字典类型，与请求类型一致", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictType;

    /** 只做字段投影，不把主键当作业务编码，不重写分类值或展示名称。 */
    public static DictDataTypeRespVO from(DictDataDO data) {
        return new DictDataTypeRespVO().setDictCode(data.getId().toString())
                .setDictLabel(data.getLabel()).setDictValue(data.getValue()).setDictType(data.getDictType());
    }
}
