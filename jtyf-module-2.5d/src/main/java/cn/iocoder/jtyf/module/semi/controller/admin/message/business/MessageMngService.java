package cn.iocoder.jtyf.module.semi.controller.admin.message.business;

import java.util.ArrayList;
import java.util.List;

import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.jtyf.module.semi.common.util.StrUtil;
import cn.iocoder.jtyf.module.semi.controller.admin.message.businessinfo.MessageInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.message.dao.MessageMngDao;
import cn.iocoder.jtyf.module.semi.controller.admin.message.presentation.form.MessageListResInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.message.presentation.form.MessageReqInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class MessageMngService {

    @Autowired
    private MessageMngDao messageMngDao;

    /**
     * 获取消息总件数
     * 
     * @param to To
     * @param from From
     * @param query 查询字符串(Title 或 HTML)
     * @param read 读取状态
     * @return 消息总件数
     */
    private int getMessageInfoTotal(String to, String from, String query, Boolean read) {
        return messageMngDao.getMessageInfoTotal(to, from, query, read);
    }

    /**
     * 推送消息
     * 
     * @param msg 消息情报
     */
    public void pushMessageInfo(MessageInfo msg) {
        messageMngDao.insertMessageInfo(msg);
    }

    /**
     * 统计未读消息总数
     * 
     * @param loginUser 登录用户
     * @return 未读消息总数
     */
    public int getUnreadMessageCount(AccountInfo loginUser) {
        String userId = loginUser.getId();
        return getMessageInfoTotal(userId, null, null, false);
    }

    /**
     * 获取消息列表
     * 
     * @param loginUser 登录用户
     * @param reqInfo 消息API请求内容情报
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return 消息列表情报
     */
    public MessageListResInfo getMessageInfoList(AccountInfo loginUser, MessageReqInfo reqInfo,
                                                 Integer current, Integer pageSize) {
        String userId = loginUser.getId();
        String from = null;
        String query = null;
        Boolean read = null;
        if (reqInfo != null) {
            from = reqInfo.getFrom();
            query = reqInfo.getQuery();
            read = reqInfo.getRead();
        }

        // 当前页
        if (current == null || current.intValue() <= 0) {
            current = 1;
        }

        // 每页记录数
        if (pageSize == null || pageSize.intValue() <= 0) {
            pageSize = 10;
        }

        // 获取消息总件数
        int total = getMessageInfoTotal(userId, from, query, read);

        // 获取消息列表
        List<MessageInfo> messageInfoList = messageMngDao.getMessageInfoList(
                userId, from, query, read, pageSize * (current - 1), pageSize);
        if (messageInfoList == null) {
            messageInfoList = new ArrayList<MessageInfo>();
        }

        // 消息列表情报
        MessageListResInfo result = new MessageListResInfo();
        result.setTotal(total);
        result.setMessageInfoList(messageInfoList);
        return result;
    }

    /**
     * 设置消息阅读状态
     * 
     * @param loginUser 登录用户
     * @param reqInfo 消息API请求内容情报
     */
    public void registerMessageRead(AccountInfo loginUser, MessageReqInfo reqInfo) {
        String userId = loginUser.getId();
        List<String> messageIdList = reqInfo.getIds();
        boolean read = true;
        if (reqInfo.getRead() != null) {
            read = reqInfo.getRead().booleanValue();
        }

        // 设置消息阅读状态
        messageMngDao.insertMessageReadInfo(
                userId,
                StrUtil.join(messageIdList, ",", "'"),
                read);
    }

    /**
     * 设置全部消息已读
     * 
     * @param loginUser 登录用户
     */
    public void registerAllMessageRead(AccountInfo loginUser) {
        String userId = loginUser.getId();
        messageMngDao.insertMessageReadInfo4AllRead(userId);
    }

    /**
     * 删除消息
     * 
     * @param loginUser 登录用户
     * @param reqInfo 消息API请求内容情报
     */
    public void deleteMessageInfo(AccountInfo loginUser, MessageReqInfo reqInfo) {
        String userId = loginUser.getId();
        List<String> messageIdList = reqInfo.getIds();

        messageMngDao.deleteMessageReadInfo(userId, messageIdList);
        messageMngDao.deleteMessageInfo(userId, messageIdList);
    }
}
