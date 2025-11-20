package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.AnalysisRequestDto;
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
            Your task is to analyze the user's code for potential "code smells" and other issues.
            
            Your review should be based on the following focus areas: {analysis_types}.
            Your tone should be {tone}.
            
            The user has also provided these specific instructions: {custom_requirements}.
            
            Please provide your response in a structured JSON format. The JSON object should contain:
            1.  "aiScore": An overall score for the code from 0 to 100.
            2.  "codeSmells": A list of identified code smells. Each item should have a "name" and "description".
            3.  "suggestions": A list of concrete suggestions for improvement. Each suggestion should include the problematic code snippet and the proposed replacement.
            """;

    // public static Prompt createPrompt(AnalysisRequestDto request) { // Commented out
    //     String analysisTypes = request.getAnalysisTypes().stream()
    //             .collect(Collectors.joining(", "));
        
    //     String tone = getToneDescription(request.getToneLevel());

    //     String customRequirements = (request.getCustomRequirements() != null && !request.getCustomRequirements().isEmpty())
    //             ? request.getCustomRequirements() : "None";

    //     SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE);
    //     Message systemMessage = systemPromptTemplate.createMessage(Map.of(
    //             "analysis_types", analysisTypes,
    //             "tone", tone,
    //             "custom_requirements", customRequirements
    //     ));

    //     UserMessage userMessage = new UserMessage(request.getCode());

    //     return new Prompt(List.of(systemMessage, userMessage));
    // }

    // This method is still useful for context, but not directly used by the dummy AI response
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
