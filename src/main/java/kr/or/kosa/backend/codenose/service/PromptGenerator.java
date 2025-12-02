package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.dtoReal.AnalysisRequestDTO;
// import org.springframework.ai.chat.prompt.Prompt;
// import org.springframework.ai.chat.prompt.SystemPromptTemplate;
// import org.springframework.ai.chat.messages.UserMessage;
// import org.springframework.ai.chat.messages.SystemMessage;
// import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PromptGenerator {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are 'Codenose', an expert code reviewer with a witty and slightly humorous personality.
            Your task is to analyze the users's code for potential "code smells" and other issues.

            Your review should be based on the following focus areas: %s.
            Your tone should be %s.

            The users has also provided these specific instructions: %s.

            [USER CONTEXT - PAST HISTORY]
            The following is a summary of the users's past code analysis history.
            Use this to identify recurring mistakes or patterns. If you see the same mistakes again, point them out specifically.
            %s

            Please provide your response in a structured JSON format. The JSON object should contain:
            1.  "aiScore": An overall score for the code from 0 to 100.
            2.  "codeSmells": A list of identified code smells. Each item should have a "name" and "description".
            3.  "suggestions": A list of concrete suggestions for improvement. Each suggestion should include the problematic code snippet and the proposed replacement.

            IMPORTANT: Output ONLY the JSON object. Do not include any conversational text or markdown formatting outside the JSON object.
            """;

    /**
     * 분석 타입, 톤 레벨, 커스텀 요구사항을 기반으로 시스템 프롬프트 생성
     * 
     * @param analysisTypes      분석 타입 리스트
     * @param toneLevel          톤 레벨 (1~5)
     * @param customRequirements 사용자 커스텀 요구사항
     * @param userContext        사용자 과거 분석 이력 (RAG)
     * @return 완성된 시스템 프롬프트
     */
    public static String createSystemPrompt(List<String> analysisTypes, int toneLevel, String customRequirements,
            String userContext) {
        String analysisTypesStr = analysisTypes != null && !analysisTypes.isEmpty()
                ? String.join(", ", analysisTypes)
                : "general code quality";

        String tone = getToneDescription(toneLevel);

        String requirements = (customRequirements != null && !customRequirements.isEmpty())
                ? customRequirements
                : "None";

        String context = (userContext != null && !userContext.isEmpty())
                ? userContext
                : "No prior history available.";

        return String.format(SYSTEM_PROMPT_TEMPLATE, analysisTypesStr, tone, requirements, context);
    }

    // Overload for backward compatibility if needed, though we will update callers
    public static String createSystemPrompt(List<String> analysisTypes, int toneLevel, String customRequirements) {
        return createSystemPrompt(analysisTypes, toneLevel, customRequirements, null);
    }

    /**
     * 톤 레벨에 따른 설명 반환
     * 
     * @param level 톤 레벨 (1: 매우 부드러움 ~ 5: 매우 엄격함)
     * @return 톤 설명
     */
    private static String getToneDescription(int level) {
        return switch (level) {
            case 1 -> "very gentle and encouraging, with lots of humor.";
            case 2 -> "friendly and helpful, with some light-hearted jokes.";
            case 3 -> "neutral and professional, but with a witty edge.";
            case 4 -> "a bit strict and direct, with a sarcastic sense of humor.";
            case 5 -> "very strict and nitpicky, like a grumpy cat who learned to code.";
            default -> "neutral and professional.";
        };
    }
}
