package kr.or.kosa.backend.codenose.service.pipeline;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 최종 합성 모듈 (FinalSynthesizerModule)
 * 
 * 역할:
 * 앞선 단계에서 추출된 스타일 규칙과 최적화된 로직을 결합하여 최종 코드를 생성합니다.
 * Mustache 템플릿 엔진을 사용하여 프롬프트를 구성합니다.
 */
@Service
public class FinalSynthesizerModule {

    private final ChatClient chatClient;
    private final Mustache.Compiler mustacheCompiler;

    public FinalSynthesizerModule(ChatClient.Builder builder, Mustache.Compiler mustacheCompiler) {
        this.chatClient = builder.build();
        this.mustacheCompiler = mustacheCompiler;
    }

    /**
     * 최종 코드 합성 수행
     * 
     * @param context 파이프라인 컨텍스트 (스타일 규칙, 최적화 로직 포함)
     * @return 업데이트된 컨텍스트 (최종 결과 설정됨)
     */
    public PipelineContext synthesize(PipelineContext context) {
        String promptTemplate = """
                You are a code synthesizer.
                Apply the following Style Rules to the Optimized Logic.

                Style Rules:
                {{styleRules}}

                Optimized Logic:
                {{optimizedLogic}}

                Output the final Java code.
                """;

        Template tmpl = mustacheCompiler.compile(promptTemplate);
        String prompt = tmpl.execute(Map.of(
                "styleRules", context.getStyleRules(),
                "optimizedLogic", context.getOptimizedLogic()));

        String finalCode = chatClient.prompt(prompt).call().content();
        context.setFinalResult(finalCode);
        return context;
    }
}
