package cn.iocoder.jtyf.module.semi.controller.admin.files.businesscontrol;

import java.util.List;

import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.controller.admin.file.business.FileMngService;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Component
public class FilesMngBusiCtrl {

    @Autowired
    private FileMngService fileMngService;

    /**
     * 批量修改文件属性内容
     * 
     * @param loginUser 登录用户
     * @param fullnameList 文件全名列表
     * @param reqInfo 文件API请求内容情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFileInfoList(AccountInfo loginUser, List<String> fullnameList, FileReqInfo reqInfo) throws CmnBizException {
        fileMngService.updateFileInfoList(loginUser, fullnameList, reqInfo);
    }

    /**
     * 批量删除文件
     * 
     * @param loginUser 登录用户
     * @param fullnameList 文件全名列表
     * @param physically 是否物理删除标识
     * @return 删除文件全名列表
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> deleteFileInfoList(AccountInfo loginUser, List<String> fullnameList, boolean physically) throws CmnBizException {
        return fileMngService.deleteFileInfoList(loginUser, fullnameList, physically);
    }

    /**
     * 批量恢复文件
     * 
     * @param loginUser 登录用户
     * @param fullnameList 文件全名列表
     * @return 恢复文件全名列表
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> restoreFileInfoList(AccountInfo loginUser, List<String> fullnameList) throws CmnBizException {
        return fileMngService.restoreFileInfoList(loginUser, fullnameList);
    }
}
