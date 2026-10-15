package cn.iocoder.wx.framework.core.aop;

import cn.iocoder.wx.framework.common.aspect.AbstractAspectSupport;
import cn.iocoder.wx.framework.common.constant.OrderConstant;

import cn.iocoder.wx.framework.common.exception.ServerException;
import cn.iocoder.wx.framework.core.LicenseVerify;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.core.annotation.Order;

import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;


/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 */
@Aspect
@Order(OrderConstant.AOP_LICENSE)
public class LicenseAspect extends AbstractAspectSupport {

    @Resource
    private LicenseVerify licenseVerify;

    // 指定切入点为License注解
    @Pointcut("@annotation(cn.iocoder.wx.framework.core.anno.License)")
    public void licenseAnnotationPointcut() {
    }


    @Pointcut("execution(* cn.iocoder.wx.module..controller..*(..))")
    public void licenseMethodPointcut() {
    }

    @Pointcut("licenseMethodPointcut() || licenseAnnotationPointcut()")
    public void licensePointcut() {
    }

    // 环绕通知
    @Around("licensePointcut()")
    public Object aroundLicense(ProceedingJoinPoint pjp) throws Throwable {
        if (licenseVerify.verify()) {
            throw new ServerException(HttpStatus.FORBIDDEN.value(), "license 已过期，请联系授权");
        }
        return pjp.proceed();
    }

}
