package cn.iocoder.wx.module.semi.common.msg;

import java.util.List;

public class Message {
    /**
     * message code.
     */
    protected String code;

    /**
     * replacement arguments.
     */
    protected Object[] args;

    /**
     * added messages
     */
    protected List<String> addedMsgs;

    public String getCode() {
        return code;
    }

    public Object[] getArgs(){
        return this.args;
    }

    public Message(String code) {
        this(code, null);
    }

    public Message(String code, Object[] args) {
        this.code = code;
        this.args = args;
    }

    public String getMsgContent() {
        return MsgResourceManager.getMessage(code, args);
    }

    public List<String> getAddedMsgs() {
        return addedMsgs;
    }

    public void setAddedMsgs(List<String> addedMsgs) {
        this.addedMsgs = addedMsgs;
    }

    public String toString() {
        return getMsgContent();
    }
}
