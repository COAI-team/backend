package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.domain.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.domain.ProblemTopic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGenerationRequestDto {

    /** 문제 난이도 (필수) */
    private ProblemDifficulty difficulty;

    /** 문제 주제/유형 (필수) - String으로 받음 */
    private String topic;

    /** 프로그래밍 언어 (선택) 기본값: ALL */
    @Builder.Default
    private String language = "ALL";

    /** 추가 요구사항 */
    private String additionalRequirements;

    /** 시간 제한 (선택, ms) */
    private Integer timeLimit;

    /** 메모리 제한 (선택) 기본값: 256MB */
    @Builder.Default
    private Integer memoryLimit = 256;

    /** 테스트케이스 수 (선택) 기본값: 5 */
    @Builder.Default
    private Integer testCaseCount = 5;

    /**
     * topic 문자열을 ProblemTopic Enum으로 변환
     * 실패 시 null 반환
     */
    public ProblemTopic getTopicAsEnum() {
        if (topic == null) {
            return null;
        }
        try {
            return ProblemTopic.fromDisplayName(topic);
        } catch (IllegalArgumentException e) {
            // 변환 실패 시 null 반환
            return null;
        }
    }
}