package cn.iocoder.jtyf.module.semi.controller.admin.directory.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.jtyf.module.semi.common.util.ListUtil;
import cn.iocoder.jtyf.module.semi.controller.admin.directory.businessinfo.DirInfo;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class DirMngDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.jtyf.module.semi.controller.admin.directory.dao.dirMng.";

    public boolean checkExistDir(String ownerId, String fullpath, int deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullpath", fullpath);
        params.put("deleted", deleted);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkExistDir", params);
        return result > 0 ? true : false;
    }

    public DirInfo getDirInfo(String ownerId, String fullpath) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullpath", fullpath);
        return sqlSession.selectOne(NAMESPACE + "getDirInfo", params);
    }

    public int getDirInfoTotal(String ownerId, String fullpath, int deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullpath", fullpath);
        params.put("deleted", deleted);
        return sqlSession.selectOne(NAMESPACE + "getDirInfoTotal", params);
    }

    public List<DirInfo> getDirInfoList(String ownerId, String fullpath, int deleted, String sort,
            int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullpath", fullpath);
        params.put("deleted", deleted);
        params.put("sort", sort);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getDirInfoList", params);
    }

    public void addDirInfo(String ownerId, String fullpath) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("fullpath", fullpath);
        sqlSession.insert(NAMESPACE + "addDirInfo", params);
    }

    public void updateFileInfo4Directory(String ownerId, String oldFullpath, String newFullpath) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("oldFullpath", oldFullpath);
        params.put("newFullpath", newFullpath);
        sqlSession.update(NAMESPACE + "updateFileInfo4Directory", params);
    }

    public void updateDirInfo(String ownerId, String oldFullpath, String newFullpath) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("oldFullpath", oldFullpath);
        params.put("newFullpath", newFullpath);
        sqlSession.update(NAMESPACE + "updateDirInfo", params);
    }

    public List<String> deleteFileInfo(String ownerId, String equalFullpaths, List<String> likeFullpaths) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("equalFullpaths", equalFullpaths);
        params.put("likeFullpaths", likeFullpaths);
        List<String> deleteFileFullnameList = sqlSession.selectList(NAMESPACE + "getFileFullnameList", params);
        if (ListUtil.isNotEmptyList(deleteFileFullnameList)) {
            params.put("fullnameList", deleteFileFullnameList);
            sqlSession.delete(NAMESPACE + "deleteFileInfo", params);
        }
        return deleteFileFullnameList;
    }

    public void deleteDirInfo(String ownerId, String equalFullpaths, List<String> likeFullpaths) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("equalFullpaths", equalFullpaths);
        params.put("likeFullpaths", likeFullpaths);
        sqlSession.delete(NAMESPACE + "deleteDirInfo", params);
    }

    public List<String> recycleFileInfo(String ownerId, String equalFullpaths, List<String> likeFullpaths, boolean recycle) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("equalFullpaths", equalFullpaths);
        params.put("likeFullpaths", likeFullpaths);
        params.put("recycle", recycle);
        List<String> recycleFileFullnameList = sqlSession.selectList(NAMESPACE + "getFileFullnameList", params);
        if (ListUtil.isNotEmptyList(recycleFileFullnameList)) {
            params.put("fullnameList", recycleFileFullnameList);
            sqlSession.update(NAMESPACE + "recycleFileInfo", params);
        }
        return recycleFileFullnameList;
    }

    public void recycleDirInfo(String ownerId, String equalFullpaths, List<String> likeFullpaths, boolean recycle) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("equalFullpaths", equalFullpaths);
        params.put("likeFullpaths", likeFullpaths);
        params.put("recycle", recycle);
        sqlSession.update(NAMESPACE + "recycleDirInfo", params);
    }
}
