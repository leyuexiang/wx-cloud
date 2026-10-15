package cn.iocoder.jtyf.module.semi.controller.admin.message.presentation.form;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.jtyf.module.semi.controller.admin.message.businessinfo.MessageInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 消息列表情报
 */
public class MessageListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<MessageInfo> messageInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<MessageInfo> getMessageInfoList() {
        return messageInfoList;
    }

    public void setMessageInfoList(List<MessageInfo> messageInfoList) {
        this.messageInfoList = messageInfoList;
    }
}
