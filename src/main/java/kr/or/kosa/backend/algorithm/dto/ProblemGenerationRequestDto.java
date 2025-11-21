package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.domain.ProblemDifficulty;
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

    /** 문제 주제/유형 (필수) */
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
}