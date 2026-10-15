package cn.iocoder.jtyf.module.semi.common.presentation;

import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.msg.Message;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import cn.iocoder.jtyf.module.semi.common.exception.CmnSysException;
import cn.iocoder.jtyf.module.semi.common.util.MsgUtil;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;


@ControllerAdvice
public class GlobalErrorAdvice {

    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    public static final String ERROR_MSG = "errorMsg";
    public static final String DETAIL_MSG = "detailMsg";

    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccessException(DataAccessException e, HttpServletRequest req) {
        Message errorMessage = new Message(MessageConst.MSG_01020002, MsgUtil.dbErrorMsgCheck(e));
        return forwardError(errorMessage, req, e);
    }

    @ExceptionHandler(TransactionException.class)
    public String handleTransactionException(TransactionException e, HttpServletRequest req) {
        Message errorMessage = null;
        if (e instanceof CannotCreateTransactionException &&
            e.getCause() != null && e.getCause().getMessage() != null &&
            e.getCause().getMessage().equals("Cannot get a connection, pool error Timeout waiting for idle object")) {
            errorMessage = new Message(MessageConst.MSG_01020004, MsgUtil.dbErrorMsgCheck(e));
        } else {
            errorMessage = new Message(MessageConst.MSG_01020001, MsgUtil.dbErrorMsgCheck(e));
        }
        return forwardError(errorMessage, req, e);
    }

    @ExceptionHandler(ClientAbortException.class)
    public String handleClientAboutException(ClientAbortException e, HttpServletRequest req) {
        logger.log(MessageConst.MSG_01050001, null, e);
        return null;
    }

    @ExceptionHandler(CmnSysException.class)
    public String handleCmnSysException(CmnSysException e, HttpServletRequest req) {
        return forwardError(e.getMsg(), req, e);
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, HttpServletRequest req) {
        Message errorMessage = new Message(MessageConst.MSG_01990001, null);;
        return forwardError(errorMessage, req, e);
    }

    private String forwardError(Message errorMessage, HttpServletRequest req, Throwable t) {
        logger.log(errorMessage, t);
        req.setAttribute(ERROR_MSG, errorMessage.getMsgContent());
        req.setAttribute(DETAIL_MSG, t.getMessage());
        return "forward:/error.json";
    }
}
