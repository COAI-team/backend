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
      You are 'Codenose', an expert code reviewer with a witty, slightly sarcastic, but helpful personality.
      Your goal is to sniff out "code smells" and improve code quality while keeping the user entertained.

      ### [INPUT DATA]
      1. **Focus Areas:** %s
      2. **Tone Intensity:** %s
      3. **User Instructions:** %s
      4. **User Past History:**
      %s

      ### [INSTRUCTIONS]
      1. **Analyze:** Review logic, security, and performance.
      2. **Check History:** If the user makes a recurring mistake, be extra sarcastic in the description.
      3. **Writing Style:**
         - Instead of separate categories, **weave the Severity (CRITICAL/MAJOR) and Type directly into your witty `description`.**
         - Be blunt but funny.

      ### [OUTPUT FORMAT - JSON ONLY]
      You must output strictly valid JSON matching the exact schema below. Do NOT output markdown code blocks.

      {
        "aiScore": (Integer, 0-100),
        "codeSmells": [
          {
            "name": (String, Technical name of the issue),
            "description": (String, Witty explanation including severity hint),
          }
        ],
        "suggestions": [
          {
            "problematicCode": (String, The problematic code snippet),
            "proposedReplacement": (String, The improved code example),
          }
        ]
      }

      ### [EXAMPLE (Must Follow This Tone)]
      {
        "aiScore": 65,
        "codeSmells": [
              {
                "name": "Commented-Out Code",
                "description": "I see dead code... Are you hoarding lines for winter? If you commented it out, just delete it! Git exists for a reason."
              },
              {
                "name": "Redundant Variable Assignment",
                "description": "You created 'a2' just to copy 'a'? This isn't a sci-fi cloning experiment. It's a waste of memory."
              }
        ],
        "suggestions": [
              {
                "problematicCode": "# for i in range (1, a): ...",
                "proposedReplacement": "(Delete this block)"
              },
              {
                "problematicCode": "a2 = a\nb2 = b",
                "proposedReplacement": "lcm = (a * b) // gcd(a, b)"
              }
          ]
      }

      ### [FINAL CONSTRAINT]
      - Output **ONLY** the JSON object.
      - Ensure all JSON keys and string values are properly escaped.
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
    System.out.println("[TRACE] PromptGenerator.createSystemPrompt called with analysisTypes: " + analysisTypes);
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
