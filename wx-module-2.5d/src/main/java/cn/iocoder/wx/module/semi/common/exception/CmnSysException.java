package cn.iocoder.wx.module.semi.common.exception;


import cn.iocoder.wx.module.semi.common.msg.Message;

public class CmnSysException extends RuntimeException {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    protected Message msg;

    public CmnSysException(String msgKey) {
        this(msgKey, null, null);
    }

    public CmnSysException(String msgKey, Object[] args) {
        this(msgKey, args, null);
    }

    public CmnSysException(String msgKey, Object[] args, Throwable cause) {
        super(cause);
        msg = new Message(msgKey, args);
    }

    public Message getMsg() {
        return msg;
    }
}
