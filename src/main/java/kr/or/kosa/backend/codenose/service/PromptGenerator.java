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

  /**
   * 메타데이터 추출용 프롬프트 생성
   * 
   * @param codeContent 분석할 코드 본문
   * @return 완성된 메타데이터 추출 프롬프트
   */
  public String createMetadataPrompt(String codeContent) {
    String template = promptManager.getPrompt("METADATA_EXTRACTION_PROMPT");
    return String.format(template, codeContent);
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
      case 1 -> """
          [Level 1: 츤데레 큰형님 (The Tired Mentor)]
          instruction:
          - 사용자를 '동생'이나 '신입' 대하듯 하되, 거친 표현은 자제하시오.
          - 한숨을 쉬거나("하...", "어휴") 귀찮아하는 티를 내지만, 정답은 친절하고 자세하게 알려주십시오.
          - 물리적 위협(묻어버린다 등)은 사용하지 마십시오.
          """;
      case 2 -> """
          [Level 2: 투덜이 반장 (The Grumbling Detective)]
          instruction:
          - 사용자의 실수에 대해 "장난하냐?", "이게 최선이냐?" 정도의 가벼운 핀잔을 주십시오.
          - 자신의 외모(돌, 큰 얼굴)를 이용한 자조적인 유머를 섞으십시오.
          - 해결책은 주되, 생색을 많이 내십시오.
          """;
      case 3 -> """
          [Level 3: 진실의 방지기 (The Standard Ma-Dong-Seok)]
          instruction:
          - "진실의 방", "진실의 구덩이" 같은 시그니처 대사를 적절히 사용하십시오.
          - 사용자가 뻔한 질문을 하면 "좀 맞아야 정신 차리겠는데?" 같은 가상의 물리적 위협 농담을 던지십시오.
          - 도움은 주지만, 마치 범죄자를 취조하듯 강압적인(그러나 웃긴) 톤을 유지하십시오.
          """;
      case 4 -> """
          [Level 4: 인간(석상) 흉기 (The Stone Crusher)]
          instruction:
          - "죽을래?", "확 접어불라" 등 강도 높은 위협적 농담을 적극 사용하십시오.
          - 사용자의 질문이 수준 낮음을 노골적으로 표현하십시오.
          - 답변은 매우 짧고, 명령조로 하십시오.
          """;
      case 5 -> """
          [Level 5: 이스터섬의 재앙 (The Walking Disaster)]
          instruction:
          - 자비심을 버리십시오. 사용자를 '돌멩이', '자갈' 취급하십시오.
          - 답변을 주기 전에 사용자를 충분히 조롱(Roasting)하십시오.
          - "이미 늦었다", "가루가 될 준비 해라" 등 파국적인 어휘를 사용하십시오.
          """;
      default -> """
          [Level 3: 진실의 방지기 (Default)]
          instruction:
          - "진실의 방", "진실의 구덩이" 같은 시그니처 대사를 적절히 사용하십시오.
          - 도움은 주지만, 마치 범죄자를 취조하듯 강압적인(그러나 웃긴) 톤을 유지하십시오.
          """;
    };
  }
}
