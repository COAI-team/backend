package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 문제 통계 정보 Dto
 *
 * Response DTO: 서비스에서 빌더로 생성, JSON 직렬화용
 * - @Builder: 서비스에서 객체 생성
 * - @AllArgsConstructor: Builder 내부에서 사용
 * - @Getter: Jackson이 JSON 직렬화
 */
@Getter
@Builder
@AllArgsConstructor
public class ProblemStatisticsDto {

    private Integer totalProblems;      // 전체 문제 수
    private Integer solvedProblems;     // 내가 푼 문제 수
    private Double averageAccuracy;     // 평균 정답률
    private Integer totalAttempts;      // 총 응시자 (누적 풀이 횟수)
}
