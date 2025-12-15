package kr.or.kosa.backend.codenose.aop;

import kr.or.kosa.backend.codenose.service.LangfuseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LangfuseAspect {

    private final LangfuseService langfuseService;

    @Around("@annotation(langfuseObserve)")
    public Object traceLangfuse(ProceedingJoinPoint joinPoint, LangfuseObserve langfuseObserve) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String traceName = langfuseObserve.name().isEmpty() ? signature.getName() : langfuseObserve.name();

        // 1. Input 데이터 수집 (Capture Input)
        Map<String, Object> inputMap = new HashMap<>();
        if (langfuseObserve.captureInput()) {
            Object[] args = joinPoint.getArgs();
            String[] paramNames = signature.getParameterNames();

            if (paramNames != null && args.length == paramNames.length) {
                for (int i = 0; i < args.length; i++) {
                    inputMap.put(paramNames[i], args[i]);
                }
            } else {
                inputMap.put("args", Arrays.toString(args));
            }
        }

        // 2. Trace/Span 시작
        Instant startTime = Instant.now();
        String spanId = langfuseService.startSpan(traceName, startTime, inputMap);

        // startSpan could return null if disabled
        if (spanId == null) {
            return joinPoint.proceed();
        }

        Object result;
        try {
            // 3. 실제 비즈니스 로직 실행
            result = joinPoint.proceed();

            // 4. Output 데이터 수집 및 종료 (Output Capture)
            Map<String, Object> outputMap = new HashMap<>();
            if (langfuseObserve.captureOutput()) {
                outputMap.put("result", result);
            }

            // endSpan에서 단순히 ID pop만 하는 것이 아니라, 필요하다면 output을 업데이트하기 위해
            // 별도 로직이 필요할 수 있으나, 현재 구현상 startSpan에서 metadata(input)은 보내고
            // endSpan에서 output을 보내려면 sendSpan을 원자적으로 다시 보내거나 해야 함.
            // 하지만 여기서는 기존 endSpan 호출 규격을 따름.
            langfuseService.endSpan(spanId, Instant.now(), outputMap);

            return result;

        } catch (Throwable e) {
            // 5. 예외 발생 시 에러 기록
            // LangfuseService에 에러 상태 업데이트 기능이 제한적이라면,
            // 별도 에러 이벤트를 보내거나 endSpan 시 에러 정보를 포함해야 함.
            // 현재는 간단히 endSpan 호출 (추후 에러 상세 지원 필요)
            Map<String, Object> errorOutput = new HashMap<>();
            errorOutput.put("error", e.getMessage());
            errorOutput.put("stackTrace", Arrays.toString(e.getStackTrace()));

            langfuseService.endSpan(spanId, Instant.now(), errorOutput);
            throw e;
        }
    }
}
