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
     * SUCCESS, FAILED, TIMEOUT, IN_PROGRESS
     */
    private GenerationStatus status;

    /**
     * 에러 메시지 (실패 시)
     */
    private String errorMessage;

    /**
     * AI 생성 상태 Enum
     */
    public enum GenerationStatus {
        SUCCESS("생성 성공"),
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
}