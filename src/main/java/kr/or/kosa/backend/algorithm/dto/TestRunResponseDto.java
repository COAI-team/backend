package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 샘플 테스트 실행 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestRunResponseDto {

    private String overallResult;      // AC, WA, CE, RE, TLE 등
    private int passedCount;           // 통과한 테스트케이스 수
    private int totalCount;            // 전체 테스트케이스 수
    private Double testPassRate;       // 통과율 (%)
    private Integer maxExecutionTime;  // 최대 실행 시간 (ms)
    private Integer maxMemoryUsage;    // 최대 메모리 사용량 (KB)
    private List<TestCaseResultDto> testCaseResults;  // 각 테스트케이스 결과

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseResultDto {
        private Integer testCaseNumber;    // 테스트케이스 번호
        private String input;              // 입력 데이터
        private String expectedOutput;     // 기대 출력
        private String actualOutput;       // 실제 출력
        private String result;             // AC, WA, TLE, RE, CE
        private Integer executionTime;     // 실행 시간 (ms)
        private Integer memoryUsage;       // 메모리 사용량 (KB)
        private String errorMessage;       // 에러 메시지 (있을 경우)
    }
}