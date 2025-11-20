package kr.or.kosa.backend.commons.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class PerformanceMonitoringAspect {
    private static final long SLOW_METHOD = 1000L;
    private static final long BAD_METHOD = 3000L;

    @Pointcut("execution(* kr.or.kosa.backend..controller..*(..))")
    public void app(){}

    @Around("app()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        log.info("START: - 메서드 시작: {} ", fullMethodName);

        Object result = null;
        boolean hasException = false; // 에러 체크
        String exceptionMessage = "";

        try{
            result = joinPoint.proceed();
            return result;
        }catch (Exception e){
            hasException = true;
            exceptionMessage = e.getMessage();
            throw e;
        }finally {
            long executionTime = System.currentTimeMillis() - startTime; // 메서드의 시작과 끝 시간 체크
            if(hasException){   // 에러 처리
                logException(fullMethodName, executionTime, exceptionMessage);
            }else{ //성공 처리
                logSuccess(fullMethodName, executionTime, result);
            }
            checkPerformanceThreshold(fullMethodName, executionTime); // 성능 체크
        }
    }

    private void logSuccess(String fullMethodName, long executionTime, Object result){
        String resultSummary = summarizeResult(result);
        log.info("SUCCESS [{}ms] - 메서드 성공: {} - 결과: {}", executionTime, fullMethodName, resultSummary);
    }
    private void logException(String fullMethodname, long executionTime, String exceptionMessage){
        log.error("ERROR [{}ms] - 메서드 실패: {} - 결과: {}",  executionTime, fullMethodname, exceptionMessage);
    }

    private String summarizeResult(Object result){
        if(result == null){
            return "null";
        }
        String resultStr = result.toString();
        if(resultStr.length() > 100){
            return resultStr.substring(0,100) + "...";
        }
        return resultStr;
    }

    private void checkPerformanceThreshold(String fullMethodName, long executionTime){
        if(executionTime > BAD_METHOD){
            log.error("VERY_SLOW [{}ms] - {} - 성능 개선 필요", executionTime, fullMethodName);
        }else if(executionTime > SLOW_METHOD){
            log.warn("SLOW [{}ms] - {} - 성능 검토 권장",executionTime,fullMethodName);
        }
    }

}
