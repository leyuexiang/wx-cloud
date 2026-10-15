package cn.iocoder.jtyf.module.dimensions.exception;

import cn.iocoder.jtyf.framework.common.exception.ErrorInfo;

/**
 * @author: cheYu
 * @desc: 错误码常量类
 * @create: 2025-06-25 10:31
 */
public enum ErrorCodeConstants implements ErrorInfo {
    PARAM_IS_NOT_EMPTY(500, "输入框的参数必选"),
    UUID_NOT_EXIST(500, "传入的UniqueId在数据库未找到，无法更改"),
    UUID_NOT_EXIST_AND_DELETED(500, "传入的uniqueId:[{}]在数据库中未找到,请上传正确的uniqueId！！同时注意：[{}]前面的uniqueId对应预警列表已删除"),
    UUID_NOT_EXIST_PLEASE_UPLOAD(500, "传入的uniqueId:[{}] 在数据库中未找到，请上传正确的uniqueId！！"),
    DEVICE_PARAM_IS_ILLEGAL(500, "请传正确的设备类型参数！"),
    PARAM_IS_ILLEGAL(500, "请传正确的参数！"),
    PARAM_NUM_IN_ARRAY_INCORRECT(500, "数组内传参错误！"),
    ONLY_TYPE_AND_SPACE_CAN_PASSED(500, "只能传type或者space!"),
    FILE_NAME_ERROR(500, "文件名错误"),
    FILE_TYPE_ERROR_PLEASE_ZIP(500, "文件类型错误,请上传zip类型的文件"),
    FILE_TYPE_ERROR_PLEASE_JSON(500, "文件类型错误,请上传json类型的文件"),
    FILE_CREATE_DIRECTORY_FAIL(500, "文件临时存储目录创建失败"),
    STORAGE_CONVERSION_TABLE_FAIL(500, "存转换表失败"),
    CHECK_FAIL_DELETE_ARCHIVE(500, "含有多个obj/fbx/3ds文件,未通过校验,删除上传的压缩包"),
    CHECK_FAIL_DELETE_ARCHIVE_FAIL(500, "含有多个obj/fbx/3ds文件,未通过校验,删除上传的压缩包失败"),
    BATCH_CONVERSION_IS_EMPTY(500, "传入id找不到对应实体，请重新上传！"),
    COMPONENT_UUID_IS_NULL(500, "componentUniqueId is null"),
    PROJECT_ID_IS_NULL(500, "projectId is null"),
    SCENE_NOT_FOUND(500, "未找到该场景"),
    SCENE_RESOURCE_INCORRECT_PLEASE_RETRY(500, "场景资源错误，请核对后重试"),
    RESOURCE_NOT_EXISTS(500, "资源信息不存在"),
    VERSION_ID_IS_NULL(500, "请传入版本号ID"),
    VERSION_ID_NOT_EXISTS(500, "版本号ID不存在"),
    ICON_INFO_UNIQUE_ID_IS_NULL(500, "请传UniqueId"),
    RESOURCE_UNIQUE_ID_IS_NULL(500, "请传UniqueId"),
    TABLE_ID_IS_NULL(500, "请传入id"),
    UUID_DATABASE_NOT_EXIST(500, "传入的uniqueId: [{}]在数据库中未找到，请上传正确的uniqueId！"),
    SCENE_ID_DATABASE_NOT_EXIST(500, "传入的sceneId: [{}]在数据库中未找到，请上传正确的sceneId！"),
    TASK_ID_DATABASE_NOT_EXIST(500, "传入的taskId: [{}]在数据库中未找到，请上传正确的taskId！"),
    ID_DATABASE_NOT_EXIST(500, "传入的id: [{}]在数据库中未找到，请上传正确的id！"),
//    EXTERNAL_TASK_ID_ILLEGAL(500, "传入的externalTaskId: [{}]不符合格式，请上传正确的externalTaskId！"),
    TASK_ID_UUID_NOT_EXIST(500, "传入的taskID和uniqueId不存在，无法更改"),
    TASK_EVENTS_IS_NULL(500, "传入的taskEvents为空，请上传正确的taskEvents！"),
    COMMAND_DEVICE_CODE_DATABASE_NOT_EXIST(500, "传入的command:[{}]，和deviceCode：[{}] 在数据库中未找到,请上传正确的command和deviceCode！"),
    VIRTUAL_CAMERA_ID_DATABASE_NOT_EXIST(500, "传入的virtualCameraId:[{}] 在数据库中未找到,请上传正确的virtualCameraId！"),
    BATCH_INSERT_ALL_ERROR(500, "批量插入全部失败"),
    VIRTUAL_CAMERA_IS_NULL(500, "传入的virtualCameras为空，请上传正确的virtualCameras！"),
    SCENE_ID_IS_NULL(500, "场景ID[sceneId]是必传项，请上传正确的场景ID[sceneId]！"),
    UPDATE_FAIL(500, "更新失败"),
    TYPE_CODE_EXIST(500, "生成的分类编码[{}]已存在，请检查分类名称是否特殊，可手动调整名称重新尝试"),
    TYPE_NAME_EXIST(500, "分类名称[{}]已存在，请更换"),
    DATA_NOT_EXIST(404, "记录不存在"),
    SAVE_TABLE_ERROR(500, "存表失败"),
    GET_NEW_TASK_LIST_FAIL(500, "获取新任务列表失败"),
    GET_TASK_LIST_FAIL(500, "获取新任务列表失败"),
    ZIP_FILE_ONLY_MODEL_FAIL(500, "压缩包只能含有一个obj/fbx/3ds/文件"),
    FILE_MOVE_FAIL(500, "文件移动失败"),
    UNZIP_FILE_IS_NULL(500, "压缩文件内文件内容为空"),
    PARAM_IS_NULL(500, "参数为空"),
    UPLOADED_JSON_FILE_CANNOT_NULL(500, "上传的JSON文件不能为空"),
    UPLOADED_FILE_TOO_LARGE(500, "上传的文件过大"),
    JSON_FILE_EMPTY(500, "JSON文件内容为空"),
    INVALID_JSON_FORMAT (500, "无效的JSON格式"),

    OVER(500, "业务未知异常");

    /**
     * 错误码
     */
    private final Integer code;
    /**
     * 错误提示
     */
    private final String msg;

    ErrorCodeConstants(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public String getMsg() {
        return this.msg;
    }

    @Override
    public Integer getCode() {
        return this.code;
    }
}