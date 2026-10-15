package cn.iocoder.wx.module.semi.controller.admin.data.business;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.util.ListUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.AuthorizeInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.DataInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.HistoryInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.dao.DataMngDao;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.form.DataReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.AuthorizeListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.DataListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.HistoryListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.util.DataConst;
import cn.iocoder.wx.module.semi.controller.admin.data.util.DataMessageConst;
import cn.iocoder.wx.module.semi.controller.admin.file.dao.FileMngDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DataMngService {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    @Autowired
    private DataMngDao dataMngDao;

    @Autowired
    private FileMngDao fileMngDao;

    /**
     * 检证是否存在数据
     * 
     * @param collection 数据分类
     * @param id 数据ID
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @return 存在的场合返回true;反之返回false
     */
    public boolean checkExistData(String collection, String id, int deleted) {
        return dataMngDao.checkExistData(collection, id, deleted);
    }

    /**
     * 获取数据总件数
     * 
     * @param ownerId 所属用户ID
     * @param dataCollection 数据分类
     * @param authCollection 权限分类
     * @param reqInfo 数据API请求内容情报
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @param userId 用户ID
     * @param departmentId 部门ID
     * @param teamIdList 团队ID列表
     * @return 数据总件数
     */
    public int getDataInfoTotal(String ownerId, String dataCollection, String authCollection, DataReqInfo reqInfo,
            int deleted, String userId, String departmentId, List<String> teamIdList) {
        if (reqInfo == null) {
            reqInfo = new DataReqInfo();
        }
        return dataMngDao.getDataInfoTotal(ownerId, dataCollection, authCollection, reqInfo, deleted,
                userId, departmentId, teamIdList);
    }

    /**
     * 获取数据列表
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @param deleted 数据删除状态(0 - 未删除;1 - 删除;Other - 全部)
     * @param operation 运营中心标识
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 数据列表情报
     * @throws CmnBizException 异常
     */
    public DataListResInfo getDataInfoList(AccountInfo loginUser, String collection,
                                           DataReqInfo reqInfo, int deleted, boolean operation,
                                           Integer current, Integer pageSize) throws CmnBizException {
        if (reqInfo == null) {
            reqInfo = new DataReqInfo();
        }

        // 是否请求系统数据
        boolean system = reqInfo.getSystem() == null ? false : reqInfo.getSystem().booleanValue();

        // 是否请求共享数据
        boolean shared = reqInfo.getShared()== null ? false : reqInfo.getShared().booleanValue();

        // 没有登录 & 非请求系统数据或共享数据的场合
/*        if (loginUser == null && !system && !shared) {
            Object[] args = new Object[] { collection, DataConst.AUTH_ACT_READ };
            logger.log(DataMessageConst.MSG_14010001, args);
            throw new CmnBizException(DataMessageConst.MSG_14010001, args);
        }*/

        String ownerId = null;
        String departmentId = null;
        List<String> roleNmList = null;
        List<String> teamIdList = null;
        if (loginUser != null) {
            ownerId = loginUser.getId();
            departmentId = loginUser.getDepartmentId();
            roleNmList = loginUser.getRoleNmList();
            teamIdList = loginUser.getTeams();
        }

    /*    // 检证权限(返回能够访问字段)
        String attributes = hasAccess(collection, null, ownerId, departmentId,
                roleNmList, teamIdList, DataConst.AUTH_ACT_READ);*/

        // 构建 Select SQL
        reqInfo.buildSelectSql("*", true, false);

        if (ListUtil.isEmptyList(reqInfo.getProjList())) {
            Object[] args = new Object[] { collection, DataConst.AUTH_ACT_READ };
            logger.log(DataMessageConst.MSG_14010001, args);
            throw new CmnBizException(DataMessageConst.MSG_14010001, args);
        }

        // 构建 Where SQL
        reqInfo.buildWhereSql();

        // 构建 Sort SQL
        String sortSql = reqInfo.buildSortSql(operation);

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 10;
        }

        String userId = ownerId;
        if (system || shared || operation) {
            userId = null;
        }

        // 获取数据总件数
        int total = getDataInfoTotal(userId, buildDataTableNm(collection), buildAuthTableNm(collection),
                reqInfo, deleted, ownerId, departmentId, teamIdList);

        // 获取数据列表
        List<DataInfo> dataInfoList = dataMngDao.getDataInfoList(userId,
                buildDataTableNm(collection), buildAuthTableNm(collection),
                reqInfo, deleted, ownerId, departmentId, teamIdList,
                sortSql, pageSize * (current - 1), pageSize);

        if (dataInfoList == null) {
            dataInfoList = new ArrayList<DataInfo>();
        } else {
            for (DataInfo dataInfo : dataInfoList) {
                dataInfo.buildDataObjectAndTeams();
            }
        }

        // 文件列表情报
        DataListResInfo result = new DataListResInfo();
        result.setTotal(total);
        result.setDataInfoList(dataInfoList);
        return result;
    }

    /**
     * 保存数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param dataInfo 数据情报
     * @return 数据情报
     * @throws CmnBizException 异常
     */
    public DataInfo saveDataInfo(AccountInfo loginUser, String collection, DataInfo dataInfo) throws CmnBizException {
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, null, ownerId, departmentId,
                roleNmList, teamIdList, DataConst.AUTH_ACT_ADD);

        // 构建所属用户和编辑用户情报
        dataInfo.buildOwnerAndEditor(loginUser.getId(), loginUser.getUsername(), DataConst.AUTH_ACT_ADD);

        // 保存数据
        if (dataInfo.getTemplate() == null) {
            dataInfo.setTemplate(false);
        }
        if (dataInfo.getSystem() == null) {
            dataInfo.setSystem(false);
        }
        if (dataInfo.getRecommend() == null) {
            dataInfo.setRecommend(0);
        }
        String dataId = dataMngDao.saveDataInfo(buildDataTableNm(collection), dataInfo);

        // 设置权限
        dataMngDao.saveAuthInfo(buildAuthTableNm(collection), dataId, loginUser.getId(),
                DataConst.AUTH_SUB_TYP_OWNER, DataConst.AUTH_ACT_OWNER, null, false);

        // 保存历史
        if (DataConst.HISTORY_DATA_COLLECTION_LIST.contains(collection)) {
            dataMngDao.saveHistoryInfo(buildHistTableNm(collection), dataId, dataInfo, null, null, loginUser.getId());
        }

        // 更新文件(缩略图：添加tag)
        if (StrUtil.isNotEmpty(dataInfo.getImage())) {
            fileMngDao.updateFileInfo4AddTag(buildDataTableNm(collection), dataId, dataId);
        }

        // 获取数据
        DataReqInfo reqInfo = new DataReqInfo();
        reqInfo.setId(dataId);
        return (DataInfo) getDataInfo(loginUser, collection, reqInfo, false);
    }

    /**
     * 获取数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @param history 是否请求历史数据
     * @return 数据情报
     * @throws CmnBizException 异常
     */
    public Object getDataInfo(AccountInfo loginUser, String collection, DataReqInfo reqInfo,
            boolean history) throws CmnBizException {
        String ownerId = null;
        String departmentId = null;
        List<String> roleNmList = null;
        List<String> teamIdList = null;
        if (loginUser != null) {
            ownerId = loginUser.getId();
            departmentId = loginUser.getDepartmentId();
            roleNmList = loginUser.getRoleNmList();
            teamIdList = loginUser.getTeams();
        }

        // 检证是否存在数据
        if (!checkExistData(buildDataTableNm(collection), reqInfo.getId(), 0)) {
            Object[] args = new Object[] { collection, reqInfo.getId() };
            logger.log(DataMessageConst.MSG_14010004, args);
            throw new CmnBizException(DataMessageConst.MSG_14010004, args);
        }

        // 检证权限(返回能够访问字段)
        String attributes = hasAccess(collection, reqInfo.getId(), ownerId, departmentId,
                roleNmList, teamIdList, DataConst.AUTH_ACT_READ);

        // 构建 Select SQL
        reqInfo.buildSelectSql(attributes, false, history);

        if (StrUtil.isEmpty(reqInfo.getProjection())) {
            Object[] args = new Object[] { collection, reqInfo.getId(), DataConst.AUTH_ACT_READ };
            logger.log(DataMessageConst.MSG_14010002, args);
            throw new CmnBizException(DataMessageConst.MSG_14010002, args);
        }

        if (!history) {
            // 查找最新数据
            List<DataInfo> dataInfoList = dataMngDao.getDataInfoList(null,
                    buildDataTableNm(collection), buildAuthTableNm(collection),
                    reqInfo, 0, null, null, null, null, 0, Integer.MAX_VALUE);

            DataInfo dataInfo = null;
            if (ListUtil.isNotEmptyList(dataInfoList)) {
                dataInfo = dataInfoList.get(0);
                dataInfo.buildDataObjectAndTeams();
            }

            if (dataInfo == null) {
                Object[] args = new Object[] { collection, reqInfo.getId() };
                logger.log(DataMessageConst.MSG_14010004, args);
                throw new CmnBizException(DataMessageConst.MSG_14010004, args);
            }

            return dataInfo;
        } else {
            // 查找历史数据
            if (!DataConst.HISTORY_DATA_COLLECTION_LIST.contains(collection)) {
                Object[] args = new Object[] { collection, reqInfo.getId() };
                logger.log(DataMessageConst.MSG_14010004, args);
                throw new CmnBizException(DataMessageConst.MSG_14010004, args);
            }

            HistoryInfo histInfo = dataMngDao.getHistoryInfo(null, buildHistTableNm(collection), reqInfo);

            if (histInfo == null) {
                Object[] args = new Object[] { collection, reqInfo.getId() };
                logger.log(DataMessageConst.MSG_14010004, args);
                throw new CmnBizException(DataMessageConst.MSG_14010004, args);
            }

            histInfo.buildDataObject();

            return histInfo;
        }
    }

    /**
     * 更新数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param dataInfo 数据情报
     * @param operation 运营中心标识
     * @return 数据情报
     * @throws CmnBizException 异常
     */
    public DataInfo updateDataInfo(AccountInfo loginUser, String collection,
            DataInfo dataInfo, boolean operation) throws CmnBizException {
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证是否存在数据
        if (!checkExistData(buildDataTableNm(collection), dataInfo.getId(), 0)) {
            Object[] args = new Object[] { collection, dataInfo.getId() };
            logger.log(DataMessageConst.MSG_14010004, args);
            throw new CmnBizException(DataMessageConst.MSG_14010004, args);
        }

        // 检证权限
        hasAccess(collection, dataInfo.getId(), ownerId, departmentId,
                roleNmList, teamIdList, DataConst.AUTH_ACT_UPDATE);

        // 构建数据JSON格式字符串
        dataInfo.buildDataJsonStr();

        // 构建所属用户和编辑用户情报
        dataInfo.buildOwnerAndEditor(loginUser.getId(), loginUser.getUsername(), DataConst.AUTH_ACT_UPDATE);

        // 构建团队列表字符串
        if (ListUtil.isNotEmptyList(dataInfo.getTeamList())) {
            dataInfo.setTeams(StrUtil.join(dataInfo.getTeamList(), ",", "'"));
        }

        // 非运营中心的场合
        if (!operation) {
            // 不允许修改[系统数据类型标识]
            dataInfo.setSystem(null);
            // 不允许修改[推荐值]
            dataInfo.setRecommend(null);
        }

        // 更新数据
        dataMngDao.updateDataInfo(buildDataTableNm(collection), dataInfo);

        // 设置权限
        if (dataInfo.getShared() != null || dataInfo.getSystem() != null) {
            boolean system = dataInfo.getSystem() == null ? false : dataInfo.getSystem();
            boolean shared = dataInfo.getShared() == null ? false : dataInfo.getShared();
            // 共享
            if (system || shared) {
                dataMngDao.saveAuthInfo(buildAuthTableNm(collection), dataInfo.getId(), "*",
                        DataConst.AUTH_SUB_TYP_OWNER, DataConst.AUTH_ACT_READ, null, true);
            // 不共享
            } else {
                dataMngDao.deleteAuthInfo(buildAuthTableNm(collection), dataInfo.getId(), "*",
                        DataConst.AUTH_SUB_TYP_OWNER);
            }
        }

        // 保存历史
        if (StrUtil.isNotEmpty(dataInfo.getDataJsonStr())
                && DataConst.HISTORY_DATA_COLLECTION_LIST.contains(collection)
                && !operation) {
            dataMngDao.saveHistoryInfo(buildHistTableNm(collection), dataInfo.getId(), dataInfo, null, null, ownerId);
        }

        // 更新文件(缩略图：添加tag)
        if (!operation && StrUtil.isNotEmpty(dataInfo.getImage())) {
            fileMngDao.updateFileInfo4AddTag(buildDataTableNm(collection), dataInfo.getId(), dataInfo.getId());
        }

        // 获取数据
        DataReqInfo reqInfo = new DataReqInfo();
        reqInfo.setId(dataInfo.getId());
        return (DataInfo) getDataInfo(loginUser, collection, reqInfo, false);
    }

    /**
     * 转让数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param dataInfo 数据情报
     * @throws CmnBizException 异常
     */
    public void transferDataInfo(AccountInfo loginUser, String collection, DataInfo dataInfo) throws CmnBizException {
        String id = dataInfo.getId();
        String ownerId = dataInfo.getOwnerId();
        String ownerName = dataInfo.getOwnerName();
        String loginUserId = loginUser.getId();
        // String departmentId = loginUser.getDepartmentId();
        // List<String> roleNmList = loginUser.getRoleNmList();
        // List<String> teamIdList = loginUser.getTeams();

        if (loginUserId.equals(ownerId)) {
            return;
        }

        // 检证权限
        hasAccess(collection, id, loginUserId, null, null, null, DataConst.AUTH_ACT_OWNER);

        // 更新数据(所有者)
        DataInfo dataInfo4Update = new DataInfo();
        dataInfo4Update.setId(id);
        dataInfo4Update.setOwnerId(ownerId);
        dataInfo4Update.setOwnerName(ownerName);
        dataMngDao.updateDataInfo(buildDataTableNm(collection), dataInfo4Update);

        // 删除权限
        dataMngDao.deleteAuthInfo(buildAuthTableNm(collection), id, ownerId, DataConst.AUTH_SUB_TYP_OWNER);

        // 添加权限
        dataMngDao.saveAuthInfo(buildAuthTableNm(collection),
                id, ownerId, DataConst.AUTH_SUB_TYP_OWNER, DataConst.AUTH_ACT_OWNER, null, false);
    }

    /**
     * 删除数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @throws CmnBizException 异常
     */
    public void deleteDataInfo(AccountInfo loginUser, String collection, DataReqInfo reqInfo) throws CmnBizException {
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证是否存在数据
        if (!checkExistData(buildDataTableNm(collection), reqInfo.getId(), 0)) {
            Object[] args = new Object[] { collection, reqInfo.getId() };
            logger.log(DataMessageConst.MSG_14010004, args);
            throw new CmnBizException(DataMessageConst.MSG_14010004, args);
        }

        // 检证权限
        hasAccess(collection, reqInfo.getId(), ownerId, departmentId,
                roleNmList, teamIdList, DataConst.AUTH_ACT_OWNER);

        // 删除文件(缩略图)
        fileMngDao.deleteFileInfo(null, reqInfo.getId());

        // 删除历史
        if (DataConst.HISTORY_DATA_COLLECTION_LIST.contains(collection)) {
            dataMngDao.deleteHistoryInfo(buildHistTableNm(collection), reqInfo.getId(), null);
        }

        // 删除权限
        dataMngDao.deleteAuthInfo(buildAuthTableNm(collection), reqInfo.getId(), null, null);

        // 删除数据
        dataMngDao.deleteDataInfo(loginUser.getId(), buildDataTableNm(collection), reqInfo.getId());
    }

    /**
     * 获取权限总件数
     * 
     * @param collection 权限分类
     * @param authInfo 权限情报
     * @return 权限总件数
     */
    private int getAuthInfoTotal(String collection, AuthorizeInfo authInfo) {
        return dataMngDao.getAuthInfoTotal(collection, authInfo);
    }

    /**
     * 获取权限列表
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param authInfo 权限情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 数据权限列表
     * @throws CmnBizException 异常
     */
    public AuthorizeListResInfo getAuthInfoList(AccountInfo loginUser, String collection, AuthorizeInfo authInfo,
                                                Integer current, Integer pageSize) throws CmnBizException {
        String dataId = authInfo.getDataId();
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, dataId, ownerId, departmentId, roleNmList, teamIdList, DataConst.AUTH_ACT_ADMIN);

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 10;
        }

        // 获取权限总件数
        int total = getAuthInfoTotal(buildAuthTableNm(collection), authInfo);

        // 获取权限列表
        List<AuthorizeInfo> authInfoList = dataMngDao.getAuthInfoList(buildAuthTableNm(collection), authInfo,
                pageSize * (current - 1), pageSize);

        if (authInfoList == null) {
            authInfoList = new ArrayList<AuthorizeInfo>();
        }

        // 历史列表情报
        AuthorizeListResInfo result = new AuthorizeListResInfo();
        result.setTotal(total);
        result.setAuthInfoList(authInfoList);
        return result;
    }

    /**
     * 数据授权
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param authInfoList 权限情报列表
     * @return 授权成功失败状态列表
     * @throws CmnBizException 异常
     */
    public List<String> authorize(AccountInfo loginUser, String collection,
            List<AuthorizeInfo> authInfoList) throws CmnBizException {
        String dataId = authInfoList.get(0).getDataId();
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, dataId, ownerId, departmentId, roleNmList, teamIdList, DataConst.AUTH_ACT_ADMIN);

        List<String> statusList = new ArrayList<String>();

        for (AuthorizeInfo authInfo : authInfoList) {
            if (authInfo.getAction() > 0 ) {
                // 添加权限
                try {
                    dataMngDao.saveAuthInfo(buildAuthTableNm(collection),
                            authInfo.getDataId(),
                            authInfo.getSubjectId(),
                            authInfo.getSubjectType(),
                            authInfo.getAction(),
                            authInfo.getAttributes(),
                            true);
                    fileMngDao.updateFileInfo4AddTag(buildDataTableNm(collection),
                            dataId, authInfo.getSubjectId());
                    statusList.add("success");
                } catch(Exception e) {
                    statusList.add(e.getMessage());
                }
            } else {
                // 移除权限
                try {
                    dataMngDao.deleteAuthInfo(buildAuthTableNm(collection),
                            authInfo.getDataId(),
                            authInfo.getSubjectId(),
                            authInfo.getSubjectType());
                    fileMngDao.updateFileInfo4RemoveTag(buildDataTableNm(collection),
                            dataId, authInfo.getSubjectId());
                    statusList.add("success");
                } catch(Exception e) {
                    statusList.add(e.getMessage());
                }
            }
        }

        // 获取权限列表
        AuthorizeInfo authInfo = new AuthorizeInfo();
        authInfo.setDataId(dataId);
        authInfo.setSubjectType(DataConst.AUTH_SUB_TYP_TEAM);
        authInfo.setAction(DataConst.AUTH_ACT_READ);
        List<AuthorizeInfo> authInfoList4Team = dataMngDao.getAuthInfoList(
                buildAuthTableNm(collection), authInfo, 0, 1000);

        if (ListUtil.isNotEmptyList(authInfoList4Team)) {
            List<String> subjectIdList = new ArrayList<String>();
            for (AuthorizeInfo authInfo4Team : authInfoList4Team) {
                subjectIdList.add(authInfo4Team.getSubjectId());
            }
            // 更新数据(团队)
            DataInfo dataInfo = new DataInfo();
            dataInfo.setId(dataId);
            dataInfo.setTeams(StrUtil.join(subjectIdList, ",", "'"));
            dataMngDao.updateDataInfo(buildDataTableNm(collection), dataInfo);
        }

        return statusList;
    }

    /**
     * 删除权限
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param authInfoList 权限情报列表
     * @return 删除成功失败状态列表
     * @throws CmnBizException 异常
     */
    public List<String> deleteAuthInfo(AccountInfo loginUser, String collection,
            List<AuthorizeInfo> authInfoList) throws CmnBizException {
        String dataId = authInfoList.get(0).getDataId();
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, dataId, ownerId, departmentId, roleNmList, teamIdList, DataConst.AUTH_ACT_ADMIN);

        List<String> statusList = new ArrayList<String>();

        for (AuthorizeInfo authInfo : authInfoList) {
            // 移除权限
            try {
                dataMngDao.deleteAuthInfo(buildAuthTableNm(collection),
                        authInfo.getDataId(),
                        authInfo.getSubjectId(),
                        authInfo.getSubjectType());
                statusList.add("success");
            } catch(Exception e) {
                statusList.add(e.getMessage());
            }
        }

        // 获取权限列表
        AuthorizeInfo authInfo = new AuthorizeInfo();
        authInfo.setDataId(dataId);
        authInfo.setSubjectType(DataConst.AUTH_SUB_TYP_TEAM);
        authInfo.setAction(DataConst.AUTH_ACT_READ);
        List<AuthorizeInfo> authInfoList4Team = dataMngDao.getAuthInfoList(
                buildAuthTableNm(collection), authInfo, 0, 1000);

        if (ListUtil.isNotEmptyList(authInfoList4Team)) {
            List<String> subjectIdList = new ArrayList<String>();
            for (AuthorizeInfo authInfo4Team : authInfoList4Team) {
                subjectIdList.add(authInfo4Team.getSubjectId());
            }
            // 更新数据(团队)
            DataInfo dataInfo = new DataInfo();
            dataInfo.setId(dataId);
            dataInfo.setTeams(StrUtil.join(subjectIdList, ",", "'"));
            dataMngDao.updateDataInfo(buildDataTableNm(collection), dataInfo);
        }

        return statusList;
    }

    /**
     * 获取历史总件数
     * 
     * @param collection 历史分类
     * @param dataId 数据ID
     * @return 历史总件数
     */
    private int getHistoryInfoTotal(String collection, String dataId) {
        return dataMngDao.getHistoryInfoTotal(collection, dataId);
    }

    /**
     * 获取历史列表
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 历史列表情报
     * @throws CmnBizException 异常
     */
    public HistoryListResInfo getHistoryInfoList(AccountInfo loginUser, String collection,
                                                 DataReqInfo reqInfo, Integer current, Integer pageSize) throws CmnBizException {
        String dataId = reqInfo.getId();
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, dataId, ownerId, departmentId, roleNmList, teamIdList, DataConst.AUTH_ACT_READ);

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 10;
        }

        // 获取历史总件数
        int total = getHistoryInfoTotal(buildHistTableNm(collection), dataId);

        // 获取历史列表
        List<HistoryInfo> historyInfoList = dataMngDao.getHistoryInfoList(buildHistTableNm(collection), dataId,
                pageSize * (current - 1), pageSize);

        if (historyInfoList == null) {
            historyInfoList = new ArrayList<HistoryInfo>();
        }

        // 历史列表情报
        HistoryListResInfo result = new HistoryListResInfo();
        result.setTotal(total);
        result.setHistoryInfoList(historyInfoList);
        return result;
    }

    /**
     * 更新历史属性内容
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param historyInfo 历史情报
     * @throws CmnBizException 异常
     */
    public void updateHistoryInfo(AccountInfo loginUser, String collection, HistoryInfo historyInfo)
            throws CmnBizException {
        String id = historyInfo.getId();
        String dataId = historyInfo.getDataId();
        String name = historyInfo.getName();
        String remarks = historyInfo.getRemarks();
        String tag = historyInfo.getTag();
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, dataId, ownerId, departmentId, roleNmList, teamIdList, DataConst.AUTH_ACT_UPDATE);

        if (StrUtil.isNotEmpty(tag)) {
            // 移除标签属性内容
            dataMngDao.updateHistoryInfo4RemoveTag(buildHistTableNm(collection), dataId, tag);
        }

        // 更新历史属性内容
        dataMngDao.updateHistoryInfo(buildHistTableNm(collection), id, name, remarks, tag);
    }

    /**
     * 删除历史
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @throws CmnBizException 异常
     */
    public void deleteHistoryInfo(AccountInfo loginUser, String collection, DataReqInfo reqInfo)
            throws CmnBizException {
        String dataId = reqInfo.getDataId();
        List<String> historyIdList = reqInfo.getHistoryIdList();
        String ownerId = loginUser.getId();
        String departmentId = loginUser.getDepartmentId();
        List<String> roleNmList = loginUser.getRoleNmList();
        List<String> teamIdList = loginUser.getTeams();

        // 检证权限
        hasAccess(collection, dataId, ownerId, departmentId, roleNmList, teamIdList, DataConst.AUTH_ACT_UPDATE);

        // 删除历史
        dataMngDao.deleteHistoryInfoList(buildHistTableNm(collection), historyIdList);
    }

    private String hasAccess(String collection, String dataId, String ownerId, String departmentId,
            List<String> roleNmList, List<String> teamIdList, int action) throws CmnBizException {

        boolean isCommonDataCollection = false;
        if (DataConst.COMMON_DATA_COLLECTION_LIST.contains(collection)) {
            isCommonDataCollection = true;
        }

        if (!isCommonDataCollection) {
            Object[] args  = new Object[] {
                    collection, dataId, ownerId, departmentId, roleNmList, teamIdList, action
            };
            logger.log(DataMessageConst.MSG_14010003, args);
            throw new CmnBizException(DataMessageConst.MSG_14010003, args);
        }

        // 所有用户都可以创建通用数据分类
        if (action == DataConst.AUTH_ACT_ADD ) {
            return "*";
        }

        if (ListUtil.isNotEmptyList(roleNmList)
                && (roleNmList.contains("系统管理员") || roleNmList.contains("运营管理员"))) {
            return "*";
        }

        if (StrUtil.isEmpty(dataId) && action == DataConst.AUTH_ACT_READ) {
            return "*";
        }

        // 查找权限表
        List<AuthorizeInfo> authorizeInfoList = dataMngDao.getAuthInfo(
                buildAuthTableNm(collection), dataId, ownerId, departmentId, roleNmList, teamIdList, action);

        if (ListUtil.isEmptyList(authorizeInfoList)) {
            Object[] args  = new Object[] {
                    collection, dataId, ownerId, departmentId, roleNmList, teamIdList, action
            };
            logger.log(DataMessageConst.MSG_14010003, args);
            throw new CmnBizException(DataMessageConst.MSG_14010003, args);
        }

        Set<String> projectionSet = new HashSet<String>();
        for (AuthorizeInfo authorizeInfo : authorizeInfoList) {
            String attributes = authorizeInfo.getAttributes();
            if (attributes != null) {
                attributes = attributes.trim();
            }
            if (StrUtil.isEmpty(attributes) || attributes.equals("*")) {
                return "*";
            }
            projectionSet.addAll(Arrays.asList(attributes.split(",")));
        }

        return StrUtil.strArrAggWithoutEmpty(projectionSet.toArray(new String[projectionSet.size()]), ", ");
    }

    public void deleteFileInfo(String image) {
        fileMngDao.deleteFileInfo(image, null);
    }

    private String buildDataTableNm(String collection) {
        return "`" + collection + "`";
    }

    private String buildAuthTableNm(String collection) {
        return "`" + collection + ".authorize`";
    }

    private String buildHistTableNm(String collection) {
        return "`" + collection + ".history`";
    }
}
