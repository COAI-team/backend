package kr.or.kosa.backend.codenose.service.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import kr.or.kosa.backend.codenose.config.PromptManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 에이전틱 워크플로우 서비스 (AgenticWorkflowService)
 * 
 * 역할:
 * LangChain4j를 사용하여 생성(Generator) -> 비평(Critic) -> 정제(Refiner)의 반복 루프를 실행합니다.
 * AI가 생성한 결과물을 스스로 검토하고 개선하여 높은 품질의 분석 결과를 도출합니다.
 */
@Slf4j
@Service
public class AgenticWorkflowService {

    private final Generator generator;
    private final Critic critic;
    private final Refiner refiner;
    private final PromptManager promptManager;

    public AgenticWorkflowService(ChatLanguageModel chatLanguageModel, PromptManager promptManager) {
        this.generator = AiServices.create(Generator.class, chatLanguageModel);
        this.critic = AiServices.create(Critic.class, chatLanguageModel);
        this.refiner = AiServices.create(Refiner.class, chatLanguageModel);
        this.promptManager = promptManager;
    }

    /**
     * 워크플로우 실행
     * 
     * 1. 생성(Generator): 사용자 코드와 시스템 프롬프트를 기반으로 초안(Draft) JSON을 생성합니다.
     * 2. 비평(Critic): 생성된 JSON이 타당한지 검토합니다.
     * 3. 정제(Refiner): 비평 내용을 반영하여 JSON을 수정합니다.
     * 2-3번 과정을 최대 maxIterations 만큼 반복하거나, 비평가가 승인할 때까지 수행합니다.
     * 
     * @param userCode     분석할 사용자 코드
     * @param systemPrompt 분석을 위한 시스템 프롬프트 (JSON 스키마 포함)
     * @return 최종 정제된 JSON 문자열
     */
    public String executeWorkflow(String userCode, String systemPrompt) {
        log.info("Agentic Workflow 시작...");

        // 1단계: 초안(Draft) 생성
        String draft = generator.generate(userCode, systemPrompt);
        log.info("초안 생성 완료.");

        // 2단계: 비평 루프 (Critic Loop)
        int maxIterations = 2; // 지연 시간 고려하여 짧게 설정
        String currentJson = draft;
        String criticSystemPrompt = promptManager.getPrompt("CRITIC_SYSTEM_PROMPT");

        for (int i = 0; i < maxIterations; i++) {
            // 비평가가 현재 JSON을 평가
            String critique = critic.critique(currentJson, criticSystemPrompt);

            // 승인 시 루프 종료
            if (critique.toLowerCase().contains("approved")) {
                log.info("비평가 승인 완료 (반복 횟수: {})", i + 1);
                return currentJson;
            }

            log.info("비평가 지적 사항: {}", critique);

            // 정제자가 지적 사항을 반영하여 수정
            currentJson = refiner.refine(currentJson, critique, systemPrompt);
            log.info("코드 정제 완료 (반복 횟수: {})", i + 1);
        }

        log.warn("최대 반복 횟수 도달. 마지막 수정본을 반환합니다.");
        return currentJson;
    }

    // --- LangChain4j AI 서비스 인터페이스 정의 ---

    // 생성자 (Generator): 코드 분석 및 JSON 초안 생성
    interface Generator {
        @SystemMessage("{{systemPrompt}}")
        String generate(@UserMessage("다음 코드를 분석하십시오:\n{{code}}") @V("code") String code,
                @V("systemPrompt") String systemPrompt);
    }

    // 비평가 (Critic): JSON 품질 평가 및 오류 지적
    interface Critic {
        @SystemMessage("{{systemPrompt}}")
        String critique(@UserMessage("다음 JSON 분석 결과를 검토하십시오:\n{{json}}") @V("json") String json,
                @V("systemPrompt") String systemPrompt);
    }

    // 정제자 (Refiner): 피드백 반영하여 JSON 수정
    interface Refiner {
        @SystemMessage("{{systemPrompt}}\n\n중요: 제공된 피드백을 바탕으로 JSON을 수정하십시오.")
        String refine(@UserMessage("현재 JSON: {{json}}\n피드백: {{feedback}}") @V("json") String json,
                @V("feedback") String feedback, @V("systemPrompt") String systemPrompt);
    }
}
