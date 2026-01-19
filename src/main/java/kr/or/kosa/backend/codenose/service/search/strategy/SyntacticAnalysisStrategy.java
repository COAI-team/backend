package kr.or.kosa.backend.codenose.service.search.strategy;

import java.util.Map;

/**
 * 구문 분석 전략 인터페이스 (SyntacticAnalysisStrategy)
 * 
 * 역할:
 * 다양한 프로그래밍 언어(Java, Python, JS 등)의 코드를 파싱하여
 * 구문적 특징(Features)을 추출하는 공통 규약을 정의합니다.
 */
public interface SyntacticAnalysisStrategy {
    /**
     * 코드 특징 추출
     * 
     * 주어진 코드에서 언어별로 정의된 중요한 특징(루프 깊이, 복잡도, 사용된 API 등)을 추출합니다.
     *
     * @param code 분석할 소스 코드
     * @return 특징 맵 (예: "max_loop_depth" -> 3, "cyclomatic_complexity" -> 5)
     */
    Map<String, Object> extractFeatures(String code);

    /**
     * 언어 지원 여부 확인
     * 
     * 이 전략이 해당 언어를 처리할 수 있는지 확인합니다.
     *
     * @param language 언어 식별자 (예: "java", "python")
     * @return 지원 여부 (true/false)
     */
    boolean supports(String language);
}
