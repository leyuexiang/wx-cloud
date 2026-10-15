package cn.iocoder.wx.module.semi.controller.admin.directory.businesscontrol;

import java.util.List;

import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.directory.business.DirMngService;
import cn.iocoder.wx.module.semi.controller.admin.directory.businessinfo.DirInfo;
import cn.iocoder.wx.module.semi.controller.admin.directory.presentation.form.DirReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.directory.presentation.model.DirListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.directory.presentation.model.DirWithFileListResInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Component
public class DirMngBusiCtrl {

    @Autowired
    private DirMngService dirMngService;

    /**
     * 获取文件夹列表
     * 
     * @param ownerId 所属用户ID
     * @param reqInfo 文件夹API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 文件夹列表情报
     */
    @Transactional(readOnly = true)
    public DirListResInfo getDirInfoList(String ownerId, DirReqInfo reqInfo,
                                         Integer current, Integer pageSize) {
        return dirMngService.getDirInfoList(ownerId, reqInfo, current, pageSize);
    }

    /**
     * 添加文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpath 文件夹路径
     * @return 文件夹情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public DirInfo addDirInfo(String ownerId, String fullpath) throws CmnBizException {
        return dirMngService.addDirInfo(ownerId, fullpath);
    }

    /**
     * 更新文件夹
     * 
     * @param ownerId 所属用户ID
     * @param oldFullpath 旧文件夹路径
     * @param newFullpath 新文件夹路径
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDirInfo(String ownerId, String oldFullpath, String newFullpath) throws CmnBizException {
        dirMngService.updateDirInfo(ownerId, oldFullpath, newFullpath);
    }

    /**
     * 删除文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpaths 文件夹列表
     * @param physically 是否物理删除标识
     * @return 文件夹名称以及文件名称列表情报
     */
    @Transactional(rollbackFor = Exception.class)
    public DirWithFileListResInfo deleteDirInfo(String ownerId, List<String> fullpaths, boolean physically) {
        return dirMngService.deleteDirInfo(ownerId, fullpaths, physically);
    }

    /**
     * 恢复文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpaths 文件夹列表
     * @return 文件夹名称以及文件名称列表情报
     */
    @Transactional(rollbackFor = Exception.class)
    public DirWithFileListResInfo restoreDirInfo(String ownerId, List<String> fullpaths) {
        return dirMngService.restoreDirInfo(ownerId, fullpaths);
    }
}
