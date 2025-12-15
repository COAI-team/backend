package kr.or.kosa.backend.codenose.service.pipeline;

import kr.or.kosa.backend.codenose.service.LangfuseService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;

/**
 * 로직 최적화 모듈 (LogicOptimizerModule)
 * 
 * 역할:
 * 입력된 코드의 로직을 분석하여 성능과 가독성을 최적화합니다.
 * 스타일보다는 알고리즘 효율성 및 모범 사례(Best Practices)에 집중합니다.
 */
@Service
public class LogicOptimizerModule {

    private final ChatClient chatClient;
    private final LangfuseService langfuseService;

    public LogicOptimizerModule(ChatClient.Builder builder,
            LangfuseService langfuseService) {
        this.chatClient = builder.build();
        this.langfuseService = langfuseService;
    }

    /**
     * 로직 최적화 수행
     * 
     * @param context 파이프라인 컨텍스트 (원본 코드 포함)
     * @return 업데이트된 컨텍스트 (최적화된 로직 설정됨)
     */
    public PipelineContext optimizeLogic(PipelineContext context) {
        Instant start = Instant.now();
        // Langfuse 스팬 시작
        langfuseService.startSpan("LogicOptimizer", start, Collections.emptyMap());

        try {
            String prompt = """
                    Optimize the following Java code for performance and readability.
                    Ignore specific style conventions for now; focus on algorithmic efficiency and best practices.
                    Output ONLY the optimized code snippet.

                    Code:
                    %s
                    """.formatted(context.getOriginalCode());

            Instant genStart = Instant.now();
            String optimized = chatClient.prompt(prompt).call().content();
            Instant genEnd = Instant.now();

            // Langfuse Generation 기록
            langfuseService.sendGeneration("OptimizeGeneration", genStart, genEnd, "gpt-4o", prompt, optimized, 0);

            context.setOptimizedLogic(optimized);
            return context;
        } finally {
            // Langfuse 스팬 종료
            langfuseService.endSpan(null, Instant.now(), Collections.emptyMap());
        }
    }
}
