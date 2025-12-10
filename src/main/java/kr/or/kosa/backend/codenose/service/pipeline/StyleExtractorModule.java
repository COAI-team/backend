package kr.or.kosa.backend.codenose.service.pipeline;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 스타일 추출 모듈 (StyleExtractorModule)
 * 
 * 역할:
 * 사용자 컨텍스트(과거 코드 이력 등)를 분석하여 선호하는 코딩 스타일(명명 규칙, 라이브러리 등)을 추출합니다.
 */
@Service
public class StyleExtractorModule {

    private final ChatClient chatClient;

    public StyleExtractorModule(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 스타일 규칙 추출
     * 
     * @param context 파이프라인 컨텍스트 (사용자 컨텍스트 포함)
     * @return 업데이트된 컨텍스트 (스타일 규칙 설정됨)
     */
    public PipelineContext extractStyle(PipelineContext context) {
        // 사용자 컨텍스트가 없으면 기본 스타일 적용
        if (context.getUserContext() == null || context.getUserContext().isEmpty()) {
            context.setStyleRules("표준 Java 컨벤션을 따르십시오.");
            return context;
        }

        String prompt = """
                Extract explicit coding style rules from the following user history.
                Focus on naming conventions, error handling patterns, and library preferences.
                Output ONLY the rules as a bulleted list.

                History:
                %s
                """.formatted(context.getUserContext());

        String rules = chatClient.prompt(prompt).call().content();
        context.setStyleRules(rules);
        return context;
    }
}
