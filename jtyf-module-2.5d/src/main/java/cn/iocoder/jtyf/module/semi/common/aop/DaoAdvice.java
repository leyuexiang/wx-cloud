package cn.iocoder.jtyf.module.semi.common.aop;

import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class DaoAdvice {
    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    @Around(value = "daoPointCut()")
    protected Object doLogAround(ProceedingJoinPoint pjp) throws Throwable {
        String clzNm = pjp.getTarget().getClass().getName();
        String methodNm = pjp.getSignature().getName();

        long t = System.currentTimeMillis();
        logStart(clzNm, methodNm);

        Object retVal = pjp.proceed();

        t = System.currentTimeMillis() - t;
        logEnd(clzNm, methodNm);
        if (logger.isDebugEnabled()) {
            debug("Time taken: " + t + "ms");
        }
        return retVal;
    }

    @Pointcut(value = "execution(* cn..web..dao..*.*(..))")
    public void daoPointCut() {}

    private void logStart(String... msg) {
        logger.log(MessageConst.MSG_01010001, msg);
    }

    private void logEnd(String... msg) {
        logger.log(MessageConst.MSG_01010002, msg);
    }

    private void debug(String msg) {
        logger.debug(msg);
    }
}
