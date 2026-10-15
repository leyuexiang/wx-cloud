package cn.iocoder.jtyf.module.semi.common.exception;


import cn.iocoder.jtyf.module.semi.common.msg.Message;

public class CmnBizException extends Exception{

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    protected Message msg;

    public CmnBizException(String msgKey) {
        this(msgKey, null, null);
    }

    public CmnBizException(String msgKey, Object[] args) {
        this(msgKey, args, null);
    }

    public CmnBizException(String msgKey, Object[] args, Throwable cause) {
        super(cause);
        msg = new Message(msgKey, args);
    }

    public Message getMsg() {
        return msg;
    }
}
