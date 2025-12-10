package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.config.PromptManager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 프롬프트 생성기 (PromptGenerator)
 * 
 * 역할:
 * AI 모델에게 전달할 시스템 프롬프트(System Prompt)를 동적으로 생성합니다.
 * 사용자가 선택한 페르소나(Tone Level)와 요구사항(Custom Requirements),
 * 그리고 이전 분석 문맥(User Context)을 조합하여 최적의 지시사항을 만듭니다.
 */
@Service
@RequiredArgsConstructor
public class PromptGenerator {

  private final PromptManager promptManager;

  /**
   * 시스템 프롬프트 생성 (핵심 메서드)
   * 
   * 분석 유형, 톤(페르소나), 사용자 요구사항, 문맥 정보를 모두 종합하여
   * 최종적으로 AI가 수행해야 할 역할과 규칙을 정의한 텍스트를 반환합니다.
   * 
   * @param analysisTypes      사용자가 요청한 분석 유형 (예: "버그 찾기", "성능 개선" 등)
   * @param toneLevel          답변 스타일/강도 (1: 부드러움 ~ 5: 매우 혹독함)
   * @param customRequirements 사용자가 직접 입력한 추가 요구사항
   * @param userContext        RAG를 통해 검색된 사용자의 과거 분석 이력 (문맥)
   * @return 완성된 시스템 프롬프트 문자열
   */
  public String createSystemPrompt(List<String> analysisTypes, int toneLevel, String customRequirements,
      String userContext) {

    // 분석 유형이 없으면 기본값 설정
    String analysisTypesStr = analysisTypes != null && !analysisTypes.isEmpty()
        ? String.join(", ", analysisTypes)
        : "general code quality";

    // 톤 레벨에 따른 상세 지침 가져오기
    String tone = getToneDescription(toneLevel);

    String requirements = (customRequirements != null && !customRequirements.isEmpty())
        ? customRequirements
        : "None";

    String context = (userContext != null && !userContext.isEmpty())
        ? userContext
        : "No prior history available.";

    // 템플릿 로드 후 값 주입
    String template = promptManager.getPrompt("CODENOSE_SYSTEM_PROMPT");
    return String.format(template, analysisTypesStr, tone, requirements, context);
  }

  /**
   * 메타데이터 추출용 프롬프트 생성
   * 
   * 코드의 구조적 정보(언어, 프레임워크, 주요 변경점 등)만 빠르게 파악하기 위한 프롬프트를 생성합니다.
   * 
   * @param codeContent 분석할 코드 본문
   * @return 메타데이터 추출 프롬프트
   */
  public String createMetadataPrompt(String codeContent) {
    String template = promptManager.getPrompt("METADATA_EXTRACTION_PROMPT");
    return String.format(template, codeContent);
  }

  // 하위 호환성을 위한 오버로딩 (Context가 없는 경우)
  public String createSystemPrompt(List<String> analysisTypes, int toneLevel, String customRequirements) {
    return createSystemPrompt(analysisTypes, toneLevel, customRequirements, null);
  }

  /**
   * 톤 레벨(페르소나) 정의
   * 
   * 사용자가 선택한 강도(1~5)에 따라 AI의 답변 스타일을 결정합니다.
   * 각 레벨은 고유한 캐릭터성(Persona)과 말투 지침을 가지고 있습니다.
   * 
   * @param level 톤 레벨 (1: 츤데레 ~ 5: 재앙)
   * @return 해당 레벨의 시스템 프롬프트 지침
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
