package kr.or.kosa.backend.codenose.service.pipeline;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

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

    public LogicOptimizerModule(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 로직 최적화 수행
     * 
     * @param context 파이프라인 컨텍스트 (원본 코드 포함)
     * @return 업데이트된 컨텍스트 (최적화된 로직 설정됨)
     */
    public PipelineContext optimizeLogic(PipelineContext context) {
        String prompt = """
                Optimize the following Java code for performance and readability.
                Ignore specific style conventions for now; focus on algorithmic efficiency and best practices.
                Output ONLY the optimized code snippet.

                Code:
                %s
                """.formatted(context.getOriginalCode());

        String optimized = chatClient.prompt(prompt).call().content();
        context.setOptimizedLogic(optimized);
        return context;
    }
}
