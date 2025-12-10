package kr.or.kosa.backend.codenose.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain 설정 (LangChainConfig)
 * 
 * 역할:
 * LangChain4j에서 사용할 LLM(Large Language Model) 인스턴스를 빈으로 등록합니다.
 * 현재 OpenAI의 GPT-4o 모델을 사용하도록 설정되어 있습니다.
 */
@Configuration
public class LangChainConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-4o") // 고성능 모델 사용
                .build();
    }
}
