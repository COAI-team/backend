package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackType;
import kr.or.kosa.backend.algorithm.dto.enums.SolveMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 코드 제출 요청 DTO (ALG-07)
 * Validation은 서비스 계층에서 처리
 *
 * 변경사항:
 * - focusSessionId → monitoringSessionId 변경
 * - solveMode 추가 (BASIC/FOCUS)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequestDto {

    private Long problemId;
    private String language; // DB 언어명 (예: "Python 3", "Java 17", "C++17")
    private String sourceCode;

    // 시간 추적 정보
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 풀이 모드 및 모니터링 세션
    private SolveMode solveMode;
    private String monitoringSessionId; // FOCUS 모드일 때만 사용

    // AI 피드백 요청 타입
    private AiFeedbackType feedbackType;

    // GitHub 커밋 요청 여부
    private Boolean requestGithubCommit;

    /**
     * 요청 데이터 검증 메서드
     */
    public void validate() {
        if (problemId == null) {
            throw new IllegalArgumentException("문제 ID는 필수입니다");
        }
        if (language == null) {
            throw new IllegalArgumentException("프로그래밍 언어는 필수입니다");
        }
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            throw new IllegalArgumentException("소스코드는 필수입니다");
        }
    }
}