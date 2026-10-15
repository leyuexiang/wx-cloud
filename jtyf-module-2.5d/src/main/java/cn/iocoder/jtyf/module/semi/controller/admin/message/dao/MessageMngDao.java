package cn.iocoder.jtyf.module.semi.controller.admin.message.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.jtyf.module.semi.controller.admin.message.businessinfo.MessageInfo;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


@Repository
public class MessageMngDao {

    @Autowired
    private SqlSessionTemplate sqlSession;

    private static final String NAMESPACE = "cn.iocoder.jtyf.module.semi.controller.admin.message.dao.messageMng.";

    public void insertMessageInfo(MessageInfo msg) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("msg", msg);
        sqlSession.insert(NAMESPACE + "insertMessageInfo", params);
    }

    public int getMessageInfoTotal(String to, String from, String query, Boolean read) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("to", to);
        params.put("from", from);
        params.put("query", query);
        params.put("read", read);
        return sqlSession.selectOne(NAMESPACE + "getMessageInfoTotal", params);
    }

    public List<MessageInfo> getMessageInfoList(String to,String from, String query, Boolean read,
            int current, int pageSize) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("to", to);
        params.put("from", from);
        params.put("query", query);
        params.put("read", read);
        params.put("current", current);
        params.put("pageSize", pageSize);
        return sqlSession.selectList(NAMESPACE + "getMessageInfoList", params);
    }

    public void deleteMessageInfo(String to, List<String> idList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("to", to);
        params.put("idList", idList);
        sqlSession.delete(NAMESPACE + "deleteMessageInfo", params);
    }

    public void insertMessageReadInfo(String userId, String messageIdListStr, boolean read) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("messageIdListStr", messageIdListStr);
        params.put("read", read);
        sqlSession.insert(NAMESPACE + "insertMessageReadInfo", params);
    }

    public void insertMessageReadInfo4AllRead(String userId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        sqlSession.insert(NAMESPACE + "insertMessageReadInfo4AllRead", params);
    }

    public void deleteMessageReadInfo(String userId, List<String> messageIdList) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("messageIdList", messageIdList);
        sqlSession.delete(NAMESPACE + "deleteMessageReadInfo", params);
    }
}
