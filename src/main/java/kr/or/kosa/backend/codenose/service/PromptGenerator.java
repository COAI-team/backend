package kr.or.kosa.backend.codenose.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptGenerator {

  private final PromptManager promptManager;

  /**
   * 분석 타입, 톤 레벨, 커스텀 요구사항을 기반으로 시스템 프롬프트 생성
   * 
   * @param analysisTypes      분석 타입 리스트
   * @param toneLevel          톤 레벨 (1~5)
   * @param customRequirements 사용자 커스텀 요구사항
   * @param userContext        사용자 과거 분석 이력 (RAG)
   * @return 완성된 시스템 프롬프트
   */
  public String createSystemPrompt(List<String> analysisTypes, int toneLevel, String customRequirements,
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

    String template = promptManager.getPrompt("CODENOSE_SYSTEM_PROMPT");
    return String.format(template, analysisTypesStr, tone, requirements, context);
  }

  // Overload for backward compatibility if needed, though we will update callers
  public String createSystemPrompt(List<String> analysisTypes, int toneLevel, String customRequirements) {
    return createSystemPrompt(analysisTypes, toneLevel, customRequirements, null);
  }

  /**
   * 톤 레벨에 따른 설명 반환
   * 
   * @param level 톤 레벨 (1: 매우 부드러움 ~ 5: 매우 엄격함)
   * @return 톤 설명
   */
  private String getToneDescription(int level) {
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
