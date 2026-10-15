package cn.iocoder.wx.module.semi.common.util;

import com.mysql.cj.exceptions.CJException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchExecutorException;

import java.sql.BatchUpdateException;

public class MsgUtil {

    public static String[] dbErrorMsgCheck(Throwable e) {
        String[] msg = new String[2];
        if(e.getCause() instanceof CJException) {
            // e = CannotCreateTransactionException or PersistenceException ...
            msg[0] = ((CJException) e.getCause()).getSQLState();
            msg[1] = ((CJException) e.getCause()).getMessage();
        } else if(e.getCause() instanceof BatchExecutorException) {
            // e = PersistenceException ...
            if(((BatchExecutorException) e.getCause()).getCause() instanceof BatchUpdateException) {
                msg[0] = ((BatchUpdateException)(((BatchExecutorException) e.getCause()).getCause())).getSQLState();
                msg[1] = ((BatchUpdateException)(((BatchExecutorException) e.getCause()).getCause())).getNextException().getMessage();
            } else {
                msg[0] = null;
                msg[1] = ((BatchExecutorException) e.getCause()).getMessage();
            }
        } else if(e.getCause() instanceof PersistenceException) {
            // e = MyBatisSystemException ...
            if(((PersistenceException) e.getCause()).getCause() instanceof IllegalArgumentException) {
                msg[0] = null;
                msg[1] = ((IllegalArgumentException)(((PersistenceException) e.getCause()).getCause())).getMessage();
            } else {
                msg[0] = null;
                msg[1] = ((PersistenceException) e.getCause()).getMessage();
            }
        } else {
            // other ...
            msg[0] = null;
            msg[1] = e.getMessage();
        }

        return msg;
    }
}
