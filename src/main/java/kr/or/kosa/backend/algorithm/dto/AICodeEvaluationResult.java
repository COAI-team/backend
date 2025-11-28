package kr.or.kosa.backend.algorithm.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AICodeEvaluationResult {

    private Double aiScore;            // AI 채점 점수
    private String feedback;           // AI가 생성한 종합 피드백

    // 부가 AI 평가 정보(선택적)
    private String codeQuality;
    private String efficiency;
    private String readability;
    private List<String> improvementTips;
}
