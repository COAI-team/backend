package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 코드 평가 결과 DTO
 *
 * 내부 전달용 DTO: 서비스 간 불변 객체로 전달
 * - @Builder: 서비스에서 객체 생성
 * - @AllArgsConstructor: Builder 내부에서 사용 (기존 누락 수정)
 * - @Getter: 값 읽기 전용
 */
@Getter
@Builder
@AllArgsConstructor
public class AICodeEvaluationResult {

    private Double aiScore;            // AI 채점 점수
    private String feedback;           // AI가 생성한 종합 피드백

    // 부가 AI 평가 정보(선택적)
    private String codeQuality;
    private String efficiency;
    private String readability;
    private List<String> improvementTips;
}
