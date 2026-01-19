package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알고리즘 테스트케이스 DTO
 * 데이터베이스 테이블: ALGO_TESTCASES
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgoTestcaseDto {

    /**
     * 테스트케이스 고유 식별자 (AUTO_INCREMENT)
     */
    private Long testcaseId;

    /**
     * 테스트 입력값
     */
    private String inputData;

    /**
     * 예상 출력값
     */
    private String expectedOutput;

    /**
     * 샘플 테스트케이스 여부 (사용자에게 공개되는 예제)
     */
    private Boolean isSample;

    /**
     * 연결된 문제 ID
     */
    private Long algoProblemId;

    // === 추가 필드 (실행 시 사용, 데이터베이스에 저장되지 않음) ===

    /**
     * 실제 출력값 (채점 시 임시 저장)
     */
    private transient String actualOutput;

    /**
     * 채점 결과 (채점 시 임시 저장)
     */
    private transient Boolean passed;

    /**
     * 실행 시간 (ms) (채점 시 임시 저장)
     */
    private transient Long executionTime;

    /**
     * 메모리 사용량 (MB) (채점 시 임시 저장)
     */
    private transient Long memoryUsage;

    /**
     * 에러 메시지 (채점 실패 시 임시 저장)
     */
    private transient String errorMessage;
}
