package kr.or.kosa.backend.algorithm.dto.response;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 문제 생성 응답 DTO
 * AI가 생성한 문제 정보 + 테스트케이스
 *
 * Phase 6 개선: 품질 등급 시스템 (AUTO_APPROVED / AUTO_REJECTED)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGenerationResponseDto {

    /**
     * 생성된 문제 ID (DB 저장 후)
     */
    private Long problemId;

    /**
     * 생성된 문제 정보
     */
    private AlgoProblemDto problem;

    /**
     * 생성된 테스트케이스 목록
     */
    private List<AlgoTestcaseDto> testCases;

    /**
     * 최적 풀이 코드
     */
    private String optimalCode;

    /**
     * 비효율적 풀이 코드 (시간 초과용)
     */
    private String naiveCode;

    /**
     * 프로그래밍 언어
     */
    private String language;

    /**
     * 검증 결과 목록
     */
    private List<ValidationResultDto> validationResults;

    /**
     * AI 생성 소요 시간 (초)
     */
    private Double generationTime;

    /**
     * 생성 완료 시간
     */
    private LocalDateTime generatedAt;

    /**
     * AI 생성 상태
     * SUCCESS, VALIDATION_FAILED, FAILED, TIMEOUT, IN_PROGRESS
     */
    private GenerationStatus status;

    /**
     * 에러 메시지 (실패 시)
     */
    private String errorMessage;

    // ===== Phase 6: 품질 등급 시스템 =====

    /**
     * 품질 검토 상태 (Phase 6)
     * AUTO_APPROVED: 모든 검증 통과 → Pool 저장 및 사용자 제공
     * AUTO_REJECTED: 검증 실패 → Pool 저장 안 함, Fallback 적용
     */
    private ReviewStatus reviewStatus;

    /**
     * 품질 검증 결과 메시지
     */
    private String message;

    /**
     * Fallback 사용 여부 (Phase 8)
     */
    private boolean fallbackUsed;

    /**
     * 추천 조합 목록 (Fallback 시 제공)
     */
    private List<String> suggestedCombinations;

    /**
     * AI 생성 상태 Enum
     */
    public enum GenerationStatus {
        SUCCESS("생성 성공"),
        VALIDATION_FAILED("검증 실패"),  // Phase 6: Self-Correction 후에도 검증 실패
        FAILED("생성 실패"),
        TIMEOUT("시간 초과"),
        IN_PROGRESS("생성 중");

        private final String description;

        GenerationStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 품질 검토 상태 Enum (Phase 6)
     *
     * 이진 품질 등급:
     * - AUTO_APPROVED: 모든 검증 통과 → 사용자에게 제공 가능
     * - AUTO_REJECTED: 하나라도 검증 실패 → 사용 불가
     */
    public enum ReviewStatus {
        AUTO_APPROVED("자동 승인"),
        AUTO_REJECTED("자동 거부");

        private final String description;

        ReviewStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 품질 검증 통과 여부 (편의 메서드)
     */
    public boolean isApproved() {
        return reviewStatus == ReviewStatus.AUTO_APPROVED;
    }
}