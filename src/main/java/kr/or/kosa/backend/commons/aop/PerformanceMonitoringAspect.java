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

    /**
     * 🔥 기존 포인트컷(잘못된 매칭):
     * execution(* kr.or.kosa.backend..controller..*(..))
     *
     * 실제로 Service, Mapper Proxy까지 매칭되어 MyBatis가 오류를 냄.
     *
     * ✔ 수정: Controller 패키지 안의 클래스만 정확히 지정
     */
    @Pointcut("within(kr.or.kosa.backend..controller..*)")
    public void controllerMethods(){}

    @Around("controllerMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();

        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        log.info("START: - 메서드 시작: {}", fullMethodName);

        Object result = null;
        boolean hasException = false;
        String exceptionMessage = "";

        try {
            result = joinPoint.proceed();
            return result;

        } catch (Exception e) {
            hasException = true;
            exceptionMessage = e.getMessage();
            throw e;

        } finally {

            long executionTime = System.currentTimeMillis() - startTime;

            if (hasException) {
                log.error("ERROR [{}ms] - 메서드 실패: {} - 결과: {}",
                        executionTime, fullMethodName, exceptionMessage);
            } else {
                String resultSummary = summarizeResult(result);
                log.info("SUCCESS [{}ms] - 메서드 성공: {} - 결과: {}",
                        executionTime, fullMethodName, resultSummary);
            }

            checkPerformanceThreshold(fullMethodName, executionTime);
        }
    }

    private String summarizeResult(Object result){
        if(result == null) return "null";

        String resultStr = result.toString();
        if(resultStr.length() > 100){
            return resultStr.substring(0,100) + "...";
        }
        return resultStr;
    }

    private void checkPerformanceThreshold(String fullMethodName, long executionTime){
        if (executionTime > BAD_METHOD) {
            log.error("VERY_SLOW [{}ms] - {} - 성능 개선 필요",
                    executionTime, fullMethodName);
        } else if (executionTime > SLOW_METHOD) {
            log.warn("SLOW [{}ms] - {} - 성능 검토 권장",
                    executionTime, fullMethodName);
        }
    }
}