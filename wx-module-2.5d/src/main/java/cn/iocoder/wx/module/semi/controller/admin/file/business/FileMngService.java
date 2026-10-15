package cn.iocoder.wx.module.semi.controller.admin.file.business;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.controller.admin.directory.business.DirMngService;
import cn.iocoder.wx.module.semi.controller.admin.file.businessinfo.FileInfo;
import cn.iocoder.wx.module.semi.controller.admin.file.dao.FileMngDao;
import cn.iocoder.wx.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.file.presentation.model.FileListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.file.util.FileMessageConst;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


@Component
public class FileMngService {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    @Autowired
    private FileMngDao fileMngDao;

    @Autowired
    private DirMngService dirMngService;

    /**
     * 获取文件总件数
     * 
     * @param ownerId 所属用户ID
     * @param authUserId 有写权限的用户ID
     * @param authTeamIdList 有写权限的团队ID列表
     * @param reqInfo 文件API请求内容情报
     * @return 文件总件数
     */
    public int getFileInfoTotal(String ownerId, String authUserId, List<String> authTeamIdList, FileReqInfo reqInfo) {
        return fileMngDao.getFileInfoTotal(ownerId, authUserId, authTeamIdList, reqInfo);
    }

    /**
     * 获取文件列表
     * 
     * @param loginUser 登录用户
     * @param reqInfo 文件API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 文件列表情报
     */
    public FileListResInfo getFileInfoList(AccountInfo loginUser, FileReqInfo reqInfo,
                                           Integer current, Integer pageSize) {
        String userId = loginUser.getId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        String ownerId = userId;
        if (ListUtil.isNotEmptyList(roleNmList)
                && (roleNmList.contains("系统管理员") || roleNmList.contains("运营管理员"))) {
            ownerId = null;
        }

        // 构建 Sort SQL
        String sortSql = reqInfo.buildSortSql();

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 10;
        }

        // 获取文件总件数
        int total = getFileInfoTotal(ownerId, userId, teamIdList, reqInfo);

        // 获取文件列表
        List<FileInfo> fileInfoList = fileMngDao.getFileInfoList(ownerId, userId, teamIdList,
                reqInfo, sortSql, pageSize * (current - 1), pageSize);
        if (fileInfoList == null) {
            fileInfoList = new ArrayList<FileInfo>();
        } else {
            for (FileInfo fileInfo : fileInfoList) {
                fileInfo.buildTagList();
            }
        }

        // 文件列表情报
        FileListResInfo result = new FileListResInfo();
        result.setTotal(total);
        result.setFileInfoList(fileInfoList);
        return result;
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
     * @param conflict 文件名冲突规则(null or "" - 覆盖;new - 重命名;error or 其它 - 返回错误)x
     * @param renameFileFlg 是否重命名文件名
     * @return 文件情报
     * @throws Exception 异常
     */
    public FileInfo saveFileInfo(AccountInfo loginUser, MultipartFile file, String name,
            String type, String filename, String directory, String tags, String remarks,
            boolean share, String conflict, boolean renameFileFlg) throws Exception {

        boolean nameParamEmpty = false;
        boolean filenameParamEmpty = false;

        // 获取源文件名
        String originalFilename = file.getOriginalFilename();

        // 获取文件后缀名
        String fileExtension = FilenameUtils.getExtension(originalFilename);

        if (StrUtil.isEmpty(filename)) {
            filename = originalFilename;
            filenameParamEmpty = true;
        }

        if (StrUtil.isEmpty(name)) {
            name = filename;
            nameParamEmpty = true;
        }

        // 设置文件类型
        if (StrUtil.isEmpty(type)) {
            type = FileUtil.judgeFileType(fileExtension);
        }

        // 设置文件目录
        if (StrUtil.isEmpty(directory)) {
            directory = "/";
        }

        // 文件情报
        FileInfo fileInfo = new FileInfo();
        fileInfo.setName(name);
        fileInfo.setType(type);
        if ((nameParamEmpty && filenameParamEmpty) || StrUtil.isEmpty(fileExtension)) {
            fileInfo.setFullname(loginUser.getId() + "_" + DateUtil.toStrForDw(new Date()) + "_" + name);
        } else {
            if (StrUtil.isNotEmpty(FilenameUtils.getExtension(name))) {
                fileInfo.setFullname(loginUser.getId() + "_" + DateUtil.toStrForDw(new Date()) + "_" + name);
            } else {
                fileInfo.setFullname(loginUser.getId() + "_" + DateUtil.toStrForDw(new Date()) + "_" + name + "." + fileExtension);
            }
        }
        fileInfo.setUrl("/api/file/" + fileInfo.getFullname());
        fileInfo.setDirectory(directory);
        if (StrUtil.isNotEmpty(tags)) {
            fileInfo.setTags(StrUtil.join(Arrays.asList(StrUtil.splitAll(tags, ",")), ",", "'"));
        }
        fileInfo.setRemarks(remarks);
        fileInfo.setShared(share);
        fileInfo.setContent(file.getBytes());
        fileInfo.setSize(file.getSize());
        fileInfo.setOwnerID(loginUser.getId());
        fileInfo.setOwnerName(loginUser.getUsername());

        if (renameFileFlg) {
            String newName = null;
            String code = ApiUtil.generateNumberCode(8);
            if (nameParamEmpty && filenameParamEmpty) {
                newName = FilenameUtils.getBaseName(name) + "." + code + "." + fileExtension;
            } else {
                newName = name + "." + code;
            }

            fileInfo.setName(newName);
            if ((nameParamEmpty && filenameParamEmpty) || StrUtil.isEmpty(fileExtension)) {
                fileInfo.setFullname(loginUser.getId() + "_" + DateUtil.toStrForDw(new Date()) + "_" + newName);
            } else {
                if (StrUtil.isNotEmpty(FilenameUtils.getExtension(newName))) {
                    fileInfo.setFullname(loginUser.getId() + "_" + DateUtil.toStrForDw(new Date()) + "_" + newName);
                } else {
                    fileInfo.setFullname(loginUser.getId() + "_" + DateUtil.toStrForDw(new Date()) + "_" + newName + "." + fileExtension);
                }
            }

            fileInfo.setUrl("/api/file/" + fileInfo.getFullname());
        }

        if (StrUtil.isNotEmpty(directory)) {
            // 添加文件夹
            try {
                dirMngService.saveDirInfo(loginUser.getId(), directory);
            } catch (CmnBizException e) {
                // nothing
            }
        }

         // 保存文件
        fileMngDao.saveFileInfo(fileInfo, conflict);

        // 获取文件
        return getFileInfoByNameAndDir(loginUser.getId(), fileInfo.getName(), fileInfo.getDirectory(), false);
    }

    public FileInfo getFileInfoByNameAndDir(String ownerId, String name, String directory, boolean contentFlg) throws CmnBizException {
        FileInfo fileInfo = fileMngDao.getFileInfoByNameAndDir(ownerId, name, directory, contentFlg);
        if (fileInfo == null) {
            Object[] args = new Object[] { name + ";" + directory };
            logger.log(FileMessageConst.MSG_13010001, args);
            throw new CmnBizException(FileMessageConst.MSG_13010001, args);
        }
        fileInfo.buildTagList();
        return fileInfo;
    }

    public FileInfo getFileInfoByFullname(AccountInfo loginUser, String fullname, boolean contentFlg) throws CmnBizException {
        String ownerId = null;
        List<String> roleNmList = null;
        if (loginUser != null) {
            ownerId = loginUser.getId();
            roleNmList = loginUser.getRoleNmList();
        }
        if (ListUtil.isNotEmptyList(roleNmList)
                && (roleNmList.contains("系统管理员") || roleNmList.contains("运营管理员"))) {
            ownerId = null;
        }
        FileInfo fileInfo = fileMngDao.getFileInfoByFullname(ownerId, fullname, contentFlg);
        if (fileInfo == null) {
            Object[] args = new Object[] { fullname };
            logger.log(FileMessageConst.MSG_13010001, args);
            throw new CmnBizException(FileMessageConst.MSG_13010001, args);
        }
        return fileInfo;
    }

    /**
     * 修改文件属性内容
     * 
     * @param loginUser 登录用户
     * @param fullnameList 文件全名列表
     * @param reqInfo 文件API请求内容情报
     * @throws CmnBizException 异常
     */
    public void updateFileInfoList(AccountInfo loginUser, List<String> fullnameList, FileReqInfo reqInfo) throws CmnBizException {
        if (ListUtil.isEmptyList(fullnameList)) {
            return;
        }

        String userId = loginUser.getId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        String ownerId = userId;
        if (ListUtil.isNotEmptyList(roleNmList)
                && (roleNmList.contains("系统管理员") || roleNmList.contains("运营管理员"))) {
            ownerId = null;
        }

        // 获取文件列表
        FileReqInfo tempFileReqInfo = new FileReqInfo();
        tempFileReqInfo.setFullnameList(fullnameList);
        tempFileReqInfo.setDeleted(0);
        List<FileInfo> fileInfoList = fileMngDao.getFileInfoList(ownerId, userId, teamIdList,
                tempFileReqInfo, null, 0, Integer.MAX_VALUE);

        List<String> notExistedFullnameList = new ArrayList<String>();

        // 文件不存在或文件已删除(所有文件)
        if (ListUtil.isEmptyList(fileInfoList)) {
            notExistedFullnameList.addAll(fullnameList);
        // 文件不存在或文件已删除(部分文件)
        } else if (fileInfoList.size() < fullnameList.size()) {
            List<String> dbFullnameList = new ArrayList<String>();
            for (FileInfo fileInfo : fileInfoList) {
                dbFullnameList.add(fileInfo.getFullname());
            }
            for (String fullname : fullnameList) {
                if (!dbFullnameList.contains(fullname)) {
                    notExistedFullnameList.add(fullname);
                }
            }
        }

        if (ListUtil.isNotEmptyList(notExistedFullnameList)) {
            Object[] args = new Object[] { StrUtil.strLstAgg(notExistedFullnameList, ",") };
            logger.log(FileMessageConst.MSG_13010001, args);
            throw new CmnBizException(FileMessageConst.MSG_13010001, args);
        }

        if (StrUtil.isNotEmpty(reqInfo.getDirectory())) {
            // 添加文件夹
            try {
                dirMngService.saveDirInfo(ownerId, reqInfo.getDirectory());
            } catch (CmnBizException e) {
                // nothing
            }
        }


        if (ListUtil.isNotEmptyList(reqInfo.getTagList())) {
            reqInfo.setTags(StrUtil.join(reqInfo.getTagList(), ",", "'"));
        }

        // 修改文件属性内容
        fileMngDao.updateFileInfoList(ownerId, fullnameList, reqInfo);
    }

    /**
     * 删除文件
     * 
     * @param loginUser 登录用户
     * @param fullnameList 文件全名列表
     * @param physically 是否物理删除标识
     * @return 删除文件全名列表
     * @throws CmnBizException 异常
     */
    public List<String> deleteFileInfoList(AccountInfo loginUser, List<String> fullnameList,
            boolean physically) throws CmnBizException {
        if (ListUtil.isEmptyList(fullnameList)) {
            return new ArrayList<String>();
        }

        String userId = loginUser.getId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        String ownerId = userId;
        if (ListUtil.isNotEmptyList(roleNmList)
                && (roleNmList.contains("系统管理员") || roleNmList.contains("运营管理员"))) {
            ownerId = null;
        }

        // 获取文件列表
        FileReqInfo tempFileReqInfo = new FileReqInfo();
        tempFileReqInfo.setFullnameList(fullnameList);
        tempFileReqInfo.setDeleted(0);
        List<FileInfo> fileInfoList = fileMngDao.getFileInfoList(ownerId, userId, teamIdList,
                tempFileReqInfo, null, 0, Integer.MAX_VALUE);

        List<String> notExistedFullnameList = new ArrayList<String>();

        // 文件不存在或文件已删除(所有文件)
        if (ListUtil.isEmptyList(fileInfoList)) {
            notExistedFullnameList.addAll(fullnameList);
        // 文件不存在或文件已删除(部分文件)
        } else if (fileInfoList.size() < fullnameList.size()) {
            notExistedFullnameList.addAll(fullnameList);
            for (FileInfo fileInfo : fileInfoList) {
                notExistedFullnameList.remove(fileInfo.getFullname());
            }
        }

        if (ListUtil.isNotEmptyList(notExistedFullnameList)) {
            Object[] args = new Object[] { StrUtil.strLstAgg(notExistedFullnameList, ",") };
            logger.log(FileMessageConst.MSG_13010001, args);
            throw new CmnBizException(FileMessageConst.MSG_13010001, args);
        }

        List<String> deletedFullnameList = null;
        if (physically) {
            // ----------
            // 物理删除
            // ----------
            // 删除文件
            deletedFullnameList = fileMngDao.deleteFileInfoList(ownerId, userId, teamIdList, fullnameList);
        } else {
            // ----------
            // 临时删除(放入回收站)
            // ----------
            // 更新文件(设置删除日期)
            deletedFullnameList = fileMngDao.recycleFileInfoList(ownerId, userId, teamIdList, fullnameList, true);
        }

        if (deletedFullnameList == null) {
            deletedFullnameList = new ArrayList<String>();
        }

        return deletedFullnameList;
    }

    /**
     * 恢复文件
     * 
     * @param loginUser 登录用户
     * @param fullnameList 文件全名列表
     * @return 恢复文件全名列表
     * @throws CmnBizException 异常
     */
    public List<String> restoreFileInfoList(AccountInfo loginUser, List<String> fullnameList) throws CmnBizException {
        if (ListUtil.isEmptyList(fullnameList)) {
            return new ArrayList<String>();
        }

        String userId = loginUser.getId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        String ownerId = userId;
        if (ListUtil.isNotEmptyList(roleNmList)
                && (roleNmList.contains("系统管理员") || roleNmList.contains("运营管理员"))) {
            ownerId = null;
        }

        // 获取文件列表
        FileReqInfo tempFileReqInfo = new FileReqInfo();
        tempFileReqInfo.setFullnameList(fullnameList);
        tempFileReqInfo.setDeleted(1);
        List<FileInfo> fileInfoList = fileMngDao.getFileInfoList(ownerId, userId, teamIdList,
                tempFileReqInfo, null, 0, Integer.MAX_VALUE);

        List<String> notExistedOrNotDeletedFullnameList = new ArrayList<String>();

        // 文件不存在或文件未删除(所有文件)
        if (ListUtil.isEmptyList(fileInfoList)) {
            notExistedOrNotDeletedFullnameList.addAll(fullnameList);
        // 文件不存在或文件未删除(部分文件)
        } else if (fileInfoList.size() < fullnameList.size()) {
            notExistedOrNotDeletedFullnameList.addAll(fullnameList);
            for (FileInfo fileInfo : fileInfoList) {
                notExistedOrNotDeletedFullnameList.remove(fileInfo.getFullname());
            }
        }

        if (ListUtil.isNotEmptyList(notExistedOrNotDeletedFullnameList)) {
            Object[] args = new Object[] { StrUtil.strLstAgg(notExistedOrNotDeletedFullnameList, ",") };
            logger.log(FileMessageConst.MSG_13010002, args);
            throw new CmnBizException(FileMessageConst.MSG_13010002, args);
        }

        // 更新文件(清空删除日期)
        List<String> restoredFullnameList = fileMngDao.recycleFileInfoList(ownerId, userId, teamIdList, fullnameList, true);

        if (restoredFullnameList == null) {
            restoredFullnameList = new ArrayList<String>();
        }

        return restoredFullnameList;
    }
}
