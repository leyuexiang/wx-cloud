package cn.iocoder.jtyf.framework.common.exception;

import cn.iocoder.jtyf.framework.common.exception.enums.ServiceErrorCodeRange;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.helpers.MessageFormatter;

/**
 * 业务逻辑异常 Exception
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceException extends RuntimeException {

    /**
     * 业务错误码
     *
     * @see ServiceErrorCodeRange
     */
    private Integer code;
    /**
     * 错误提示
     */
    private String message;

    /**
     * 空构造方法，避免反序列化问题
     */
    public ServiceException() {
    }

    public static ServiceException of(ErrorInfo errorInfo) {
        return new ServiceException(errorInfo);
    }

    public ServiceException(ErrorInfo errorInfo) {
        this.code = errorInfo.getCode();
        this.message = errorInfo.getMsg();
    }

    ServiceException(ErrorInfo errorInfo, Throwable t) {
        this.code = errorInfo.getCode();
        this.message = MessageFormatter.format(errorInfo.getMsg(), t.getMessage()).getMessage();
    }

    ServiceException(ErrorInfo errorInfo, Throwable t, Object... args) {
        this.code = errorInfo.getCode();
        this.message = MessageFormatter.arrayFormat(errorInfo.getMsg(), args, t).getMessage();
    }

    ServiceException(ErrorInfo errorInfo, Object... args) {
        this.code = errorInfo.getCode();
        this.message = MessageFormatter.arrayFormat(errorInfo.getMsg(), args).getMessage();
    }

    public ServiceException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public ServiceException setCode(Integer code) {
        this.code = code;
        return this;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public ServiceException setMessage(String message) {
        this.message = message;
        return this;
    }

}
