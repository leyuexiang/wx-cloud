package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode;
import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** 导入查询的只读投影；调用前必须完成资源操作的鉴权和一致性读取。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ModelImportStatusView(String operationId,String resourceId,String importStatus,String operationStatus,
                                    String targetFormat,String attemptId,String committedRevision,
                                    String errorCode,Integer errorNumber,String errorMessage,boolean retryable) {
    private static final Set<String> TASK_STATES=Set.of("RECEIVING","QUEUED","VERIFYING_SOURCE","CONVERTING","VALIDATING","PUBLISHING","COMMITTED","FAILED","CANCELLED","CONFLICT","EXPIRED");
    private static final Set<String> OP_STATES=Set.of("UPLOADING","VERIFYING","COMMITTED","FAILED","CANCELLED","CONFLICT","EXPIRED");
    private static final Set<String> TERMINAL=Set.of("CANCELLED","CONFLICT","EXPIRED");

    public static ModelImportStatusView from(String operationId,String resourceId,String taskState,String operationState,
                                              String format,String attemptId,String revision,ModelImportErrorCode error,
                                              boolean expired) {
        if(taskState==null || operationState==null || !TASK_STATES.contains(taskState) || !OP_STATES.contains(operationState)
                || format==null || !Set.of("gltf","glb").contains(format)) invalid();
        if("COMMITTED".equals(operationState)) {
            // 先看成功事实再看截止时间：提交后查询过期不能把已发布内容误报为失败。
            if(revision==null || !revision.matches("[1-9][0-9]{0,18}")) invalid();
            try { if(Long.parseLong(revision)<=0) invalid(); } catch(NumberFormatException ex) { invalid(); }
            return new ModelImportStatusView(operationId,resourceId,"COMMITTED",operationState,format,attemptId,revision,null,null,null,false);
        }
        if("COMMITTED".equals(taskState)) invalid();
        String state=TERMINAL.contains(operationState)?operationState:expired?"EXPIRED":"FAILED".equals(operationState)?"FAILED":taskState;
        // 历史错误只在失败状态公开，不能把运行中或终态旧错误作为可重试指令。
        ModelImportErrorCode visibleError="FAILED".equals(state)?error:null;
        boolean retryable=visibleError!=null && visibleError.isRetryable() && !expired
                && Set.of("UPLOADING","FAILED").contains(operationState);
        return new ModelImportStatusView(operationId,resourceId,state,operationState,format,attemptId,null,
                visibleError==null?null:visibleError.name(),visibleError==null?null:visibleError.getCode(),
                visibleError==null?null:visibleError.getMsg(),retryable);
    }
    private static void invalid() { throw ServiceException.of(ArtResourceErrorCode.INVALID_OPERATION_STATE); }
}
