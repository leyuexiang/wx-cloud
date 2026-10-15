package cn.iocoder.wx.module.semi.controller.admin.message.businesscontrol;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.message.business.MessageMngService;
import cn.iocoder.wx.module.semi.controller.admin.message.presentation.form.MessageListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.message.presentation.form.MessageReqInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Component
public class MessageMngBusiCtrl {

    @Autowired
    private MessageMngService messageMngService;

    /**
     * 统计未读消息总数
     * 
     * @param loginUser 登录用户
     * @return 未读消息总数
     */
    @Transactional(readOnly = true)
    public int getUnreadMessageCount(AccountInfo loginUser) {
        return messageMngService.getUnreadMessageCount(loginUser);
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
    @Transactional(readOnly = true)
    public MessageListResInfo getMessageInfoList(AccountInfo loginUser, MessageReqInfo reqInfo,
                                                 Integer current, Integer pageSize) {
        return messageMngService.getMessageInfoList(loginUser, reqInfo, current, pageSize);
    }

    /**
     * 设置消息阅读状态
     * 
     * @param loginUser 登录用户
     * @param reqInfo 消息API请求内容情报
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerMessageRead(AccountInfo loginUser, MessageReqInfo reqInfo) {
        messageMngService.registerMessageRead(loginUser, reqInfo);
    }

    /**
     * 设置全部消息已读
     * 
     * @param loginUser 登录用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerAllMessageRead(AccountInfo loginUser) {
        messageMngService.registerAllMessageRead(loginUser);
    }

    /**
     * 删除消息
     * 
     * @param loginUser 登录用户
     * @param reqInfo 消息API请求内容情报
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessageInfo(AccountInfo loginUser, MessageReqInfo reqInfo) {
        messageMngService.deleteMessageInfo(loginUser, reqInfo);
    }
}
