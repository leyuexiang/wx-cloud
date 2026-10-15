package cn.iocoder.jtyf.module.semi.controller.admin.file.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.jtyf.module.semi.common.util.ListUtil;
import cn.iocoder.jtyf.module.semi.controller.admin.file.businessinfo.FileInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class FileMngDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.jtyf.module.semi.controller.admin.file.dao.fileMng.";

    public int getFileInfoTotal(String ownerId, String authUserId, List<String> authTeamIdList,
            FileReqInfo fileInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("authUserId", authUserId);
        params.put("authTeamIdList", authTeamIdList);
        params.put("fileInfo", fileInfo);
        return sqlSession.selectOne(NAMESPACE + "getFileInfoTotal", params);
    }

    public List<FileInfo> getFileInfoList(String ownerId, String authUserId, List<String> authTeamIdList,
                                          FileReqInfo fileInfo, String sort, int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("authUserId", authUserId);
        params.put("authTeamIdList", authTeamIdList);
        params.put("fileInfo", fileInfo);
        params.put("sort", sort);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getFileInfoList", params);
    }

    public void saveFileInfo(FileInfo fileInfo, String conflict) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("fileInfo", fileInfo);
        params.put("conflict", conflict);
        sqlSession.insert(NAMESPACE + "saveFileInfo", params);
    }

    public FileInfo getFileInfoByNameAndDir(String ownerId, String name, String directory, boolean contentFlg) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("name", name);
        params.put("directory", directory);
        params.put("contentFlg", contentFlg);
        return sqlSession.selectOne(NAMESPACE + "getFileInfoByNameAndDir", params);
    }

    public FileInfo getFileInfoByFullname(String ownerId, String fullname, boolean contentFlg) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullname", fullname);
        params.put("contentFlg", contentFlg);
        return sqlSession.selectOne(NAMESPACE + "getFileInfoByFullname", params);
    }

    public void updateFileInfo4AddTag(String collection, String dataId, String tag) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("tag", tag);
        sqlSession.update(NAMESPACE + "updateFileInfo4AddTag", params);
    }

    public void updateFileInfo4RemoveTag(String collection, String dataId, String tag) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("tag", tag);
        sqlSession.update(NAMESPACE + "updateFileInfo4RemoveTag", params);
    }

    public void updateFileInfoList(String ownerId, List<String> fullnameList, FileReqInfo fileInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullnameList", fullnameList);
        params.put("fileInfo", fileInfo);
        sqlSession.update(NAMESPACE + "updateFileInfoList", params);
    }

    public void deleteFileInfo(String url, String tag) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("url", url);
        params.put("tag", tag);
        sqlSession.selectList(NAMESPACE + "deleteFileInfo", params);
    }

    public List<String> deleteFileInfoList(String ownerId, String authUserId, List<String> authTeamIdList,
            List<String> fullnameList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("authUserId", authUserId);
        params.put("authTeamIdList", authTeamIdList);
        params.put("fullnameList", fullnameList);
        List<String> deleteFileFullnameList = sqlSession.selectList(NAMESPACE + "getFileFullnameList", params);
        if (ListUtil.isNotEmptyList(deleteFileFullnameList)) {
            params.put("fullnameList", deleteFileFullnameList);
            sqlSession.delete(NAMESPACE + "deleteFileInfoList", params);
        }
        return deleteFileFullnameList;
    }

    public List<String> recycleFileInfoList(String ownerId, String authUserId, List<String> authTeamIdList,
            List<String> fullnameList, boolean recycle) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("authUserId", authUserId);
        params.put("authTeamIdList", authTeamIdList);
        params.put("fullnameList", fullnameList);
        params.put("recycle", recycle);
        List<String> recycleFileFullnameList = sqlSession.selectList(NAMESPACE + "getFileFullnameList", params);
        if (ListUtil.isNotEmptyList(recycleFileFullnameList)) {
            params.put("fullnameList", recycleFileFullnameList);
            sqlSession.update(NAMESPACE + "recycleFileInfoList", params);
        }
        return recycleFileFullnameList;
    }
}
