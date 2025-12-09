package kr.or.kosa.backend.algorithm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 문제 통계 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class ProblemStatisticsResponseDto {

    private Integer totalProblems;      // 전체 문제 수
    private Integer solvedProblems;     // 내가 푼 문제 수
    private Double averageAccuracy;     // 평균 정답률
    private Integer totalAttempts;      // 총 응시자 (누적 풀이 횟수)
}
