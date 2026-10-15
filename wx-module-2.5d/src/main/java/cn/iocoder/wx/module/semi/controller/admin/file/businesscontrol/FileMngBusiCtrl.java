package cn.iocoder.wx.module.semi.controller.admin.file.businesscontrol;

import java.util.Arrays;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.file.business.FileMngService;
import cn.iocoder.wx.module.semi.controller.admin.file.businessinfo.FileInfo;
import cn.iocoder.wx.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.file.presentation.model.FileListResInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


@Transactional
@Component
public class FileMngBusiCtrl {

    @Autowired
    private FileMngService fileMngService;

    /**
     * 获取文件列表
     * 
     * @param loginUser 登录用户
     * @param reqInfo 文件API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 文件列表情报
     */
    @Transactional(readOnly = true)
    public FileListResInfo getFileInfoList(AccountInfo loginUser, FileReqInfo reqInfo,
                                           Integer current, Integer pageSize) {
        return fileMngService.getFileInfoList(loginUser, reqInfo, current, pageSize);
    }

    /**
     * 保存文件
     * 
     * @param loginUser 登录用户情报
     * @param file 文件
     * @param name 文件名
     * @param type 文件类型
     * @param filename 文件名
     * @param directory 文件目录
     * @param tags 标签
     * @param remarks 备注
     * @param share 是否共享
     * @param conflict 文件名冲突规则(null or "" - 覆盖;new - 重命名;error or 其它 - 返回错误)
     * @param renameFileFlg 是否重命名文件名
     * @return 文件情报
     * @throws Exception 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public FileInfo saveFileInfo(AccountInfo loginUser, MultipartFile file, String name,
                                 String type, String filename, String directory, String tags, String remarks,
                                 boolean share, String conflict, boolean renameFileFlg) throws Exception {
        return fileMngService.saveFileInfo(loginUser, file, name, type, filename, directory,
                tags, remarks, share, conflict, renameFileFlg);
    }

    /**
     * 获取文件
     * 
     * @param loginUser 登录用户
     * @param fullname 文件全名
     * @return 文件情报
     * @throws CmnBizException 异常
     */
    @Transactional(readOnly = true)
    public FileInfo getFileInfo(AccountInfo loginUser, String fullname) throws CmnBizException {
        return fileMngService.getFileInfoByFullname(loginUser, fullname, true);
    }

    /**
     * 修改文件属性内容
     * 
     * @param loginUser 登录用户
     * @param fullname 文件全名
     * @param reqInfo 文件API请求内容情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFileInfo(AccountInfo loginUser, String fullname, FileReqInfo reqInfo) throws CmnBizException {
        fileMngService.updateFileInfoList(loginUser, Arrays.asList(fullname), reqInfo);
    }

    /**
     * 删除文件
     * 
     * @param loginUser 登录用户
     * @param fullname 文件全名
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileInfo(AccountInfo loginUser, String fullname) throws CmnBizException {
        fileMngService.deleteFileInfoList(loginUser, Arrays.asList(fullname), true);
    }
}
