package cn.iocoder.jtyf.module.semi.controller.admin.data.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.jtyf.module.semi.common.util.JsonUtil;
import cn.iocoder.jtyf.module.semi.controller.admin.data.businessinfo.AuthorizeInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.data.businessinfo.DataInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.data.businessinfo.HistoryInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.data.presentation.form.DataReqInfo;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class DataMngDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.jtyf.module.semi.controller.admin.data.dao.dataMng.";

    public boolean checkExistData(String collection, String id, int deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("id", id);
        params.put("deleted", deleted);
        Integer result = sqlSession.selectOne(NAMESPACE + "checkExistData", params);
        return result > 0 ? true : false;
    }

    public List<AuthorizeInfo> getAuthInfo(String collection, String dataId, String ownerId,
                                           String departmentId, List<String> roleNmList, List<String> teamIdList, int action) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("ownerId", ownerId);
        params.put("departmentId", departmentId);
        params.put("roleNmList", roleNmList);
        params.put("teamIdList", teamIdList);
        params.put("action", action);
        return sqlSession.selectList(NAMESPACE + "getAuthInfo", params);
    }

    public void saveAuthInfo(String collection, String dataId, String subjectId,
            int subjectType, int action, String attributes, boolean conflict) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("subjectId", subjectId);
        params.put("subjectType", subjectType);
        params.put("action", action);
        params.put("attributes", attributes);
        params.put("conflict", conflict);
        sqlSession.insert(NAMESPACE + "saveAuthInfo", params);
    }

    public void deleteAuthInfo(String collection, String dataId, String subjectId, Integer subjectType) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("subjectId", subjectId);
        params.put("subjectType", subjectType);
        sqlSession.delete(NAMESPACE + "deleteAuthInfo", params);
    }

    public int getDataInfoTotal(String ownerId, String dataCollection, String authCollection, DataReqInfo dataInfo,
            int deleted, String userId, String departmentId, List<String> teamIdList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("dataCollection", dataCollection);
        params.put("authCollection", authCollection);
        params.put("dataInfo", dataInfo);
        params.put("deleted", deleted);
        params.put("userId", userId);
        params.put("departmentId", departmentId);
        params.put("teamIdList", teamIdList);
        return sqlSession.selectOne(NAMESPACE + "getDataInfoTotal", params);
    }

    public List<DataInfo> getDataInfoList(String ownerId, String dataCollection, String authCollection, DataReqInfo dataInfo,
                                          int deleted, String userId, String departmentId, List<String> teamIdList, String sort, int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("dataCollection", dataCollection);
        params.put("authCollection", authCollection);
        params.put("dataInfo", dataInfo);
        params.put("deleted", deleted);
        params.put("userId", userId);
        params.put("departmentId", departmentId);
        params.put("teamIdList", teamIdList);
        params.put("sort", sort);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getDataInfoList", params);
    }

    public String saveDataInfo(String collection, DataInfo dataInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataInfo", dataInfo);
        params.put("dataJsonStr", JsonUtil.toJsonStr(dataInfo.getData()));
        sqlSession.insert(NAMESPACE + "saveDataInfo", params);
        return dataInfo.getId();
    }

    public DataInfo getDataInfo(String ownerId, String collection, String id, int deleted) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("collection", collection);
        params.put("id", id);
        params.put("deleted", deleted);
        return sqlSession.selectOne(NAMESPACE + "getDataInfo", params);
    }

    public void updateDataInfo(String collection, DataInfo dataInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataInfo", dataInfo);
        sqlSession.update(NAMESPACE + "updateDataInfo", params);
    }

    public void deleteDataInfo(String ownerId, String collection, String id) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("collection", collection);
        params.put("id", id);
        sqlSession.delete(NAMESPACE + "deleteDataInfo", params);
    }

    public int getAuthInfoTotal(String collection, AuthorizeInfo authInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("authInfo", authInfo);
        return sqlSession.selectOne(NAMESPACE + "getAuthInfoTotal", params);
    }

    public List<AuthorizeInfo> getAuthInfoList(String collection, AuthorizeInfo authInfo,
            int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("authInfo", authInfo);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getAuthInfoList", params);
    }

    public int getHistoryInfoTotal(String collection, String dataId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        return sqlSession.selectOne(NAMESPACE + "getHistoryInfoTotal", params);
    }

    public List<HistoryInfo> getHistoryInfoList(String collection, String dataId,
                                                int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getHistoryInfoList", params);
    }

    public HistoryInfo getHistoryInfo(String ownerId, String collection, DataReqInfo dataInfo) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("ownerId", ownerId);
        params.put("collection", collection);
        params.put("dataInfo", dataInfo);
        return sqlSession.selectOne(NAMESPACE + "getHistoryInfo", params);
    }

    public void saveHistoryInfo(String collection, String dataId, DataInfo dataInfo,
            String remarks, String tag, String ownerId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("dataInfo", dataInfo);
        params.put("dataJsonStr", JsonUtil.toJsonStr(dataInfo.getData()));
        params.put("remarks", remarks);
        params.put("tag", tag);
        params.put("ownerId", ownerId);
        sqlSession.insert(NAMESPACE + "saveHistoryInfo", params);
    }

    public void updateHistoryInfo(String collection, String id, String name, String remarks, String tag) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("id", id);
        params.put("name", name);
        params.put("remarks", remarks);
        params.put("tag", tag);
        sqlSession.update(NAMESPACE + "updateHistoryInfo", params);
    }

    public void updateHistoryInfo4RemoveTag(String collection, String dataId, String tag) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("tag", tag);
        sqlSession.update(NAMESPACE + "updateHistoryInfo4RemoveTag", params);
    }

    public void deleteHistoryInfo(String collection, String dataId, String ownerId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("dataId", dataId);
        params.put("ownerId", ownerId);
        sqlSession.delete(NAMESPACE + "deleteHistoryInfo", params);
    }

    public void deleteHistoryInfoList(String collection, List<String> idList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("collection", collection);
        params.put("idList", idList);
        sqlSession.delete(NAMESPACE + "deleteHistoryInfoList", params);
    }
}
