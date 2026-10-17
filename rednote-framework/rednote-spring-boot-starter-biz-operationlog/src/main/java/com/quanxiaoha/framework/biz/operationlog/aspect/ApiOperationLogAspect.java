package com.quanxiaoha.framework.biz.operationlog.aspect;

import com.quanxiaoha.framework.common.util.JsonUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/** 统一记录带有 {@link ApiOperationLog} 注解的方法调用。 */
@Aspect
public class ApiOperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiOperationLogAspect.class);

    @Around(value = "@annotation(operationLog)", argNames = "joinPoint,operationLog")
    public Object around(ProceedingJoinPoint joinPoint, ApiOperationLog operationLog) throws Throwable {
        long start = System.nanoTime();
        String method = joinPoint.getSignature().toLongString();
        String description = operationLog.description();

        if (log.isInfoEnabled()) {
            log.info("业务操作开始：description={}, method={}, args={}",
                    description, method, toJsonSafely(joinPoint.getArgs()));
        }

        try {
            Object result = joinPoint.proceed();
            if (log.isInfoEnabled()) {
                log.info("业务操作完成：description={}, method={}, elapsedMs={}, result={}",
                        description, method, elapsedMillis(start), toJsonSafely(result));
            }
            return result;
        } catch (Throwable ex) {
            log.error("业务操作失败：description={}, method={}, elapsedMs={}",
                    description, method, elapsedMillis(start), ex);
            // 保留原始异常，交由业务调用方或统一异常处理器处理。
            throw ex;
        }
    }

    private static long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static String toJsonSafely(Object value) {
        try {
            return JsonUtils.toJsonString(value);
        } catch (RuntimeException ex) {
            // 日志数据无法序列化时，仍应继续执行原有业务流程。
            return "<JSON序列化失败>";
        }
    }
}