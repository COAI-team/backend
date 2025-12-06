package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 문제 목록 응답 Dto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemListResponseDto {

    // 문제 기본 정보
    private Long algoProblemId;
    private String algoProblemTitle;
    private ProblemDifficulty algoProblemDifficulty;
    private ProblemSource algoProblemSource;
    private String language;

    // 사용자 풀이 정보
    private Boolean solved;              // 사용자가 풀었는지 여부
    private Integer userAttemptCount;    // 사용자의 시도 횟수

    // 문제 통계
    private Integer totalAttempts;       // 총 시도 횟수
    private Integer successCount;        // 성공 횟수
    private Double accuracy;             // 정답률 (%)

    // 생성 정보
    private LocalDateTime algoCreatedAt;
}
