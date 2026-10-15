package cn.iocoder.wx.module.semi.controller.admin.data.businesscontrol;

import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.data.business.DataMngService;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.AuthorizeInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.DataInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.HistoryInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.form.DataReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.AuthorizeListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.DataListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.HistoryListResInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Component
public class DataMngBusiCtrl {

    @Autowired
    private DataMngService dataMngService;

    /**
     * 获取数据列表
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 数据列表情报
     * @throws CmnBizException 异常
     */
    @Transactional(readOnly = true)
    public DataListResInfo getDataInfoList(AccountInfo loginUser, String collection,
                                           DataReqInfo reqInfo, Integer current, Integer pageSize) throws CmnBizException {
        return dataMngService.getDataInfoList(loginUser, collection, reqInfo, 0, false, current, pageSize);
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
    @Transactional(rollbackFor = Exception.class)
    public DataInfo saveDataInfo(AccountInfo loginUser, String collection, DataInfo dataInfo) throws CmnBizException {
        return dataMngService.saveDataInfo(loginUser, collection, dataInfo);
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
    @Transactional(readOnly = true)
    public Object getDataInfo(AccountInfo loginUser, String collection, DataReqInfo reqInfo,
            boolean history) throws CmnBizException {
        return dataMngService.getDataInfo(loginUser, collection, reqInfo, history);
    }

    /**
     * 更新数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param dataInfo 数据情报
     * @return 数据情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public DataInfo updateDataInfo(AccountInfo loginUser, String collection, DataInfo dataInfo) throws CmnBizException {
        return dataMngService.updateDataInfo(loginUser, collection, dataInfo, false);
    }

    /**
     * 转让数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param dataInfo 数据情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferDataInfo(AccountInfo loginUser, String collection, DataInfo dataInfo) throws CmnBizException {
        dataMngService.transferDataInfo(loginUser, collection, dataInfo);
    }

    /**
     * 删除数据
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataInfo(AccountInfo loginUser, String collection, DataReqInfo reqInfo) throws CmnBizException {
        dataMngService.deleteDataInfo(loginUser, collection, reqInfo);
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
    @Transactional(readOnly = true)
    public AuthorizeListResInfo getAuthInfoList(AccountInfo loginUser, String collection, AuthorizeInfo authInfo,
                                                Integer current, Integer pageSize) throws CmnBizException {
        return dataMngService.getAuthInfoList(loginUser, collection, authInfo, current, pageSize);
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
    @Transactional(rollbackFor = Exception.class)
    public List<String> authorize(AccountInfo loginUser, String collection,
            List<AuthorizeInfo> authInfoList) throws CmnBizException {
        return dataMngService.authorize(loginUser, collection, authInfoList);
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
    @Transactional(rollbackFor = Exception.class)
    public List<String> deleteAuthInfo(AccountInfo loginUser, String collection,
            List<AuthorizeInfo> authInfoList) throws CmnBizException {
        return dataMngService.deleteAuthInfo(loginUser, collection, authInfoList);
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
    @Transactional(readOnly = true)
    public HistoryListResInfo getHistoryInfoList(AccountInfo loginUser, String collection,
                                                 DataReqInfo reqInfo, Integer current, Integer pageSize) throws CmnBizException {
        return dataMngService.getHistoryInfoList(loginUser, collection, reqInfo, current, pageSize);
    }

    /**
     * 更新历史属性内容
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param historyInfo 历史情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateHistoryInfo(AccountInfo loginUser, String collection, HistoryInfo historyInfo) throws CmnBizException {
        dataMngService.updateHistoryInfo(loginUser, collection, historyInfo);
    }

    /**
     * 删除历史
     * 
     * @param loginUser 登录用户
     * @param collection 数据分类
     * @param reqInfo 数据API请求内容情报
     * @throws CmnBizException 异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteHistoryInfo(AccountInfo loginUser, String collection, DataReqInfo reqInfo) throws CmnBizException {
        dataMngService.deleteHistoryInfo(loginUser, collection, reqInfo);
    }

    /**
     * 删除文件
     * 
     * @param image 图片访问地址
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileInfo(String image) {
        dataMngService.deleteFileInfo(image);
    }
}
