package cn.iocoder.wx.framework.common.exception;

/**
 * @author: cheYu
 * @desc: 统一的业务异常，不可变，不允许new，只能通过静态方法创建
 * @create: 2025-06-25 09:41
 */
public final class WxBusinessException extends ServiceException {

    private WxBusinessException(Integer code, String msg) {
        super(code, msg);
    }

    private WxBusinessException(ErrorInfo errorInfo) {
        super(errorInfo);
    }

    private WxBusinessException(ErrorInfo errorInfo, Throwable t) {
        super(errorInfo, t);
    }

    private WxBusinessException(ErrorInfo errorInfo, Object... args) {
        super(errorInfo, args);
    }

    private WxBusinessException(ErrorInfo errorInfo, Throwable t, Object... args) {
        super(errorInfo, t, args);
    }

    public static WxBusinessException of(Integer code, String msg) {
        return new WxBusinessException(code, msg);
    }

    public static WxBusinessException of(ErrorInfo errorInfo) {
        return new WxBusinessException(errorInfo);
    }

    public static WxBusinessException of(ErrorInfo errorInfo, Throwable t) {
        return new WxBusinessException(errorInfo, t);
    }

    public static WxBusinessException of(ErrorInfo errorInfo, Object... args) {
        return new WxBusinessException(errorInfo, args);
    }

    public static WxBusinessException of(ErrorInfo errorInfo, Throwable t, Object... args) {
        return new WxBusinessException(errorInfo, t, args);
    }
}
