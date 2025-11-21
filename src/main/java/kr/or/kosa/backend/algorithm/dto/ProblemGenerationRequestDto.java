package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.domain.ProblemDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 문제 생성 요청 DTO
 * POST /api/algo/problems/generate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGenerationRequestDto {

    /**
     * 문제 난이도 (필수)
     * BRONZE, SILVER, GOLD, PLATINUM
     */
    private ProblemDifficulty difficulty;

    /**
     * 문제 주제/유형 (필수)
     * 예: "DP", "그래프", "그리디", "구현", "수학"
     */
    private String topic;

    /**
     * 프로그래밍 언어 (선택)
     * 기본값: "ALL" (모든 언어 지원)
     */
    private String language = "ALL";

    /**
     * 추가 요구사항 (선택)
     * 예: "초보자용으로 쉽게", "실무 면접 수준"
     */
    private String additionalRequirements;

    /**
     * 시간 제한 (선택, ms)
     * 기본값: 난이도에 따라 자동 설정
     */
    private Integer timeLimit;

    /**
     * 메모리 제한 (선택, MB)
     * 기본값: 256MB
     */
    private Integer memoryLimit = 256;

    /**
     * 생성할 테스트케이스 수 (선택)
     * 기본값: 5개
     */
    private Integer testCaseCount = 5;
}