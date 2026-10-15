package cn.iocoder.jtyf.module.semi.controller.admin.directory.business;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.util.StrUtil;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.businessinfo.DirInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.dao.DirMngDao;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.presentation.form.DirReqInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.presentation.model.DirListResInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.presentation.model.DirWithFileListResInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.util.DirMessageConst;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DirMngService {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    @Autowired
    private DirMngDao dirMngDao;

    /**
     * 检证是否存在文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpath 文件夹路径
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @return 存在的场合返回true;反之返回false
     */
    public boolean checkExistDir(String ownerId, String fullpath, int deleted) {
        return dirMngDao.checkExistDir(ownerId, fullpath, deleted);
    }

    /**
     * 获取文件夹总件数
     * 
     * @param ownerId 所属用户ID
     * @param fullpath 文件夹父路径
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @return 文件夹总件数
     */
    private int getDirInfoTotal(String ownerId, String fullpath, int deleted) {
        return dirMngDao.getDirInfoTotal(ownerId, fullpath, deleted);
    }

    /**
     * 保存文件夹情报
     * 
     * @param ownerId 所属用户ID
     * @param fullpath 文件夹路径
     * @throws CmnBizException 异常
     */
    public void saveDirInfo(String ownerId, String fullpath) throws CmnBizException {
        // 检证文件夹是否已经存在
        if (checkExistDir(ownerId, fullpath, -1)) {
            Object[] args = new Object[] { fullpath };
            logger.log(DirMessageConst.MSG_12010001, args);
            throw new CmnBizException(DirMessageConst.MSG_12010001, args);
        }

        // 添加文件夹
        dirMngDao.addDirInfo(ownerId, fullpath);
    }

    /**
     * 获取文件夹情报
     * 
     * @param ownerId 所属用户ID
     * @param fullpath 文件夹路径
     * @return 文件夹情报
     * @throws CmnBizException 异常
     */
    public DirInfo getDirInfo(String ownerId, String fullpath) throws CmnBizException {
        DirInfo result = dirMngDao.getDirInfo(ownerId, fullpath);
        if (result == null) {
            Object[] args = new Object[] { fullpath };
            logger.log(DirMessageConst.MSG_12010002, args);
            throw new CmnBizException(DirMessageConst.MSG_12010002, args);
        }
        return result;
    }

    /**
     * 获取文件夹列表
     * 
     * @param ownerId 所属用户ID
     * @param reqInfo 文件夹API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 文件夹列表情报
     */
    public DirListResInfo getDirInfoList(String ownerId, DirReqInfo reqInfo,
                                         Integer current, Integer pageSize) {
        // 构建 Sort SQL
        String sortSql = reqInfo.buildSortSql();

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 1000;
        }

        // 获取文件夹总件数
        int total = getDirInfoTotal(ownerId, reqInfo.getFullpath(), reqInfo.getDeleted());

        // 获取文件夹列表
        List<DirInfo> dirInfoList = dirMngDao.getDirInfoList(ownerId,
                reqInfo.getFullpath(), reqInfo.getDeleted(), sortSql,
                pageSize * (current - 1), pageSize);
        if (dirInfoList == null) {
            dirInfoList = new ArrayList<DirInfo>();
        }

        // 文件夹列表情报
        DirListResInfo result = new DirListResInfo();
        result.setTotal(total);
        result.setDirInfoList(dirInfoList);
        return result;
    }

    /**
     * 添加文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpath 文件夹路径
     * @return 文件夹情报
     * @throws CmnBizException 异常
     */
    public DirInfo addDirInfo(String ownerId, String fullpath) throws CmnBizException {
        // 保存文件夹情报
        saveDirInfo(ownerId, fullpath);

        // 获取文件夹情报
        return getDirInfo(ownerId, fullpath);
    }

    /**
     * 更新文件夹
     * 
     * @param ownerId 所属用户ID
     * @param oldFullpath 旧文件夹路径
     * @param newFullpath 新文件夹路径
     * @throws CmnBizException 异常
     */
    public void updateDirInfo(String ownerId, String oldFullpath, String newFullpath) throws CmnBizException {
        // 检证旧文件夹是否还存在
        if (!checkExistDir(ownerId, oldFullpath, -1)) {
            Object[] args = new Object[] { newFullpath };
            logger.log(DirMessageConst.MSG_12010002, args);
            throw new CmnBizException(DirMessageConst.MSG_12010002, args);
        }

        // 检证新文件夹是否已经存在
        if (checkExistDir(ownerId, newFullpath, -1)) {
            Object[] args = new Object[] { newFullpath };
            logger.log(DirMessageConst.MSG_12010001, args);
            throw new CmnBizException(DirMessageConst.MSG_12010001, args);
        }

        // 更新文件(所属文件夹名称)
        dirMngDao.updateFileInfo4Directory(ownerId, oldFullpath, newFullpath);

        // 更新文件夹
        dirMngDao.updateDirInfo(ownerId, oldFullpath, newFullpath);
    }

    /**
     * 删除文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpaths 文件夹列表
     * @param physically 是否物理删除标识
     * @return 文件夹名称以及文件名称列表情报
     */
    public DirWithFileListResInfo deleteDirInfo(String ownerId, List<String> fullpaths, boolean physically) {
        DirWithFileListResInfo result = new DirWithFileListResInfo();

        List<String> fileList = null;
        String equalFullpaths = StrUtil.join(fullpaths, ",", "'");
        String likeFullpaths = StrUtil.join(fullpaths, ",", "", "/%");
        List<String> likeFullpathList = Arrays.asList(StrUtil.splitAll(likeFullpaths, ","));

        if (physically) {
            // ----------
            // 物理删除
            // ----------
            // 删除文件
            fileList = dirMngDao.deleteFileInfo(ownerId, equalFullpaths, likeFullpathList);

            // 删除文件夹
            dirMngDao.deleteDirInfo(ownerId, equalFullpaths, likeFullpathList);
        } else {
            // ----------
            // 临时删除(放入回收站)
            // ----------
            // 更新文件(设置删除日期)
            fileList = dirMngDao.recycleFileInfo(ownerId, equalFullpaths, likeFullpathList, true);

            // 更新文件夹(设置删除日期)
            dirMngDao.recycleDirInfo(ownerId, equalFullpaths, likeFullpathList, true);
        }

        if (fileList == null) {
            fileList = new ArrayList<String>();
        }
        result.setDirectories(fullpaths);
        result.setFiles(fileList);

        return result;
    }

    /**
     * 恢复文件夹
     * 
     * @param ownerId 所属用户ID
     * @param fullpaths 文件夹列表
     * @return 文件夹名称以及文件名称列表情报
     */
    public DirWithFileListResInfo restoreDirInfo(String ownerId, List<String> fullpaths) {
        DirWithFileListResInfo result = new DirWithFileListResInfo();

        List<String> fileList = null;
        String equalFullpaths = StrUtil.join(fullpaths, ",", "'");
        String likeFullpaths = StrUtil.join(fullpaths, ",", "", "/%");
        List<String> likeFullpathList = Arrays.asList(StrUtil.splitAll(likeFullpaths, ","));

        // 更新文件(清除删除日期)
        fileList = dirMngDao.recycleFileInfo(ownerId, equalFullpaths, likeFullpathList, false);

        // 更新文件夹(清除删除日期)
        dirMngDao.recycleDirInfo(ownerId, equalFullpaths, likeFullpathList, false);

        if (fileList == null) {
            fileList = new ArrayList<String>();
        }
        result.setDirectories(fullpaths);
        result.setFiles(fileList);

        return result;
    }
}
