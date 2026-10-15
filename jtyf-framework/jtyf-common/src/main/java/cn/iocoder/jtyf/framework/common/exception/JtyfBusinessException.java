package cn.iocoder.jtyf.framework.common.exception;

/**
 * @author: cheYu
 * @desc: 统一的业务异常，不可变，不允许new，只能通过静态方法创建
 * @create: 2025-06-25 09:41
 */
public final class JtyfBusinessException extends ServiceException {

    private JtyfBusinessException(Integer code, String msg) {
        super(code, msg);
    }

    private JtyfBusinessException(ErrorInfo errorInfo) {
        super(errorInfo);
    }

    private JtyfBusinessException(ErrorInfo errorInfo, Throwable t) {
        super(errorInfo, t);
    }

    private JtyfBusinessException(ErrorInfo errorInfo, Object... args) {
        super(errorInfo, args);
    }

    private JtyfBusinessException(ErrorInfo errorInfo, Throwable t, Object... args) {
        super(errorInfo, t, args);
    }

    public static JtyfBusinessException of(Integer code, String msg) {
        return new JtyfBusinessException(code, msg);
    }

    public static JtyfBusinessException of(ErrorInfo errorInfo) {
        return new JtyfBusinessException(errorInfo);
    }

    public static JtyfBusinessException of(ErrorInfo errorInfo, Throwable t) {
        return new JtyfBusinessException(errorInfo, t);
    }

    public static JtyfBusinessException of(ErrorInfo errorInfo, Object... args) {
        return new JtyfBusinessException(errorInfo, args);
    }

    public static JtyfBusinessException of(ErrorInfo errorInfo, Throwable t, Object... args) {
        return new JtyfBusinessException(errorInfo, t, args);
    }
}
