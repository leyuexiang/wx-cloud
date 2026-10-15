package cn.iocoder.jtyf.framework.common.exception;

import cn.iocoder.jtyf.framework.common.exception.enums.GlobalErrorCodeConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.helpers.MessageFormatter;

/**
 * 服务器异常 Exception
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class ServerException extends RuntimeException {

    /**
     * 全局错误码
     *
     * @see GlobalErrorCodeConstants
     */
    private Integer code;
    /**
     * 错误提示
     */
    private String message;

    /**
     * 空构造方法，避免反序列化问题
     */
    public ServerException() {
    }

    public ServerException(ErrorInfo errorInfo) {
        this.code = errorInfo.getCode();
        this.message = errorInfo.getMsg();
    }

    public ServerException(Integer code, String message, Object... args) {
        this.code = code;
        this.message = MessageFormatter.arrayFormat(message, args).getMessage();
    }

    public Integer getCode() {
        return code;
    }

    public ServerException setCode(Integer code) {
        this.code = code;
        return this;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public ServerException setMessage(String message) {
        this.message = message;
        return this;
    }

}
