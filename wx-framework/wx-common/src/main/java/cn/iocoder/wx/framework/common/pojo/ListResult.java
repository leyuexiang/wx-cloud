package cn.iocoder.wx.framework.common.pojo;

import cn.iocoder.wx.framework.common.exception.ErrorCode;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.exception.enums.GlobalErrorCodeConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * @author: cheYu
 * @desc: 返回的列表结构体
 * @create: 2025-06-24 18:19
 */
@Data
public class ListResult<T>  implements Serializable {

    /**
     * 错误码
     *
     * @see ErrorCode#getCode()
     */
    private Integer code;
    /**
     * 返回数据
     */
    private List<T> data;
    /**
     * 错误提示，用户可阅读
     *
     * @see ErrorCode#getMsg() ()
     */
    private String msg;

    public static <T> ListResult<T> error(ListResult<T> result) {
        return error(result.getCode(), result.getMsg());
    }

    public static <T> ListResult<T> error(Integer code, String message) {
        Assert.isTrue(!GlobalErrorCodeConstants.SUCCESS.getCode().equals(code), "code 必须是错误的！");
        ListResult<T> result = new ListResult<>();
        result.code = code;
        result.msg = message;
        return result;
    }

    public static <T> ListResult<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMsg());
    }

    public static <T> ListResult<T> success(List<T> data) {
        ListResult<T> result = new ListResult<>();
        result.code = GlobalErrorCodeConstants.SUCCESS.getCode();
        result.data = data;
        result.msg = "";
        return result;
    }

    public static boolean isSuccess(Integer code) {
        return Objects.equals(code, GlobalErrorCodeConstants.SUCCESS.getCode());
    }

    @JsonIgnore // 避免 jackson 序列化
    public boolean isSuccess() {
        return isSuccess(code);
    }

    @JsonIgnore // 避免 jackson 序列化
    public boolean isError() {
        return !isSuccess();
    }

    // ========= 和 Exception 异常体系集成 =========

    /**
     * 判断是否有异常。如果有，则抛出 {@link ServiceException} 异常
     */
    public void checkError() throws ServiceException {
        if (isSuccess()) {
            return;
        }
        // 业务异常
        throw new ServiceException(code, msg);
    }

    /**
     * 判断是否有异常。如果有，则抛出 {@link ServiceException} 异常
     * 如果没有，则返回 {@link #data} 数据
     */
    @JsonIgnore // 避免 jackson 序列化
    public List<T> getCheckedData() {
        checkError();
        return data;
    }

    public static <T> ListResult<T> error(ServiceException serviceException) {
        return error(serviceException.getCode(), serviceException.getMessage());
    }
}
