package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 문제 풀 DTO
 * 데이터베이스 테이블: ALGO_PROBLEM_POOL
 *
 * <p>역할: 사전 생성된 AI 문제를 임시 저장하는 풀
 * <p>흐름: 풀에서 소비 → ALGO_PROBLEMS로 이동 → 풀에서 삭제
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoolProblemDto {

    /**
     * 풀 문제 고유 식별자 (AUTO_INCREMENT)
     */
    private Long algoPoolId;

    /**
     * 난이도 (BRONZE, SILVER, GOLD, PLATINUM)
     */
    private String difficulty;

    /**
     * 알고리즘 주제 (ProblemTopic enum의 displayName)
     */
    private String topic;

    /**
     * 스토리 테마 (계절별 변경 가능)
     */
    private String theme;

    /**
     * 문제 전체 데이터 (JSON 문자열)
     * - title, description, testcases, constraints 등 포함
     * - 서비스에서 AlgoProblemDto로 파싱하여 사용
     */
    private String problemContent;

    /**
     * 문제 생성 시각
     */
    private LocalDateTime generatedAt;

    /**
     * LLM 생성 소요 시간 (ms)
     */
    private Integer generationTimeMs;

    /**
     * 레코드 생성 시각
     */
    private LocalDateTime createdAt;
}
