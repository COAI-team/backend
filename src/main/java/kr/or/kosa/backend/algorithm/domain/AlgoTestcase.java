package kr.or.kosa.backend.algorithm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 알고리즘 테스트케이스 엔티티
 * 데이터베이스 테이블: ALGO_TESTCASES
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgoTestcase {

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
     * true: 샘플 (공개), false: 히든 (비공개)
     */
    private Boolean isSample;

    /**
     * 연결된 문제 ID
     */
    private Long algoProblemId;

    // === 추가 필드 (실행 시 사용) ===

    /**
     * 실제 출력값 (채점 시 임시 저장)
     * 데이터베이스에는 저장되지 않음
     */
    private transient String actualOutput;

    /**
     * 채점 결과 (채점 시 임시 저장)
     * 데이터베이스에는 저장되지 않음
     */
    private transient Boolean passed;

    /**
     * 실행 시간 (ms) (채점 시 임시 저장)
     * 데이터베이스에는 저장되지 않음
     */
    private transient Long executionTime;

    /**
     * 메모리 사용량 (MB) (채점 시 임시 저장)
     * 데이터베이스에는 저장되지 않음
     */
    private transient Long memoryUsage;

    /**
     * 에러 메시지 (채점 실패 시 임시 저장)
     * 데이터베이스에는 저장되지 않음
     */
    private transient String errorMessage;

    // === 편의 메서드 ===

    /**
     * 샘플 테스트케이스인지 확인
     */
    public boolean isSample() {
        return Boolean.TRUE.equals(isSample);
    }

    /**
     * 히든 테스트케이스인지 확인
     */
    public boolean isHidden() {
        return !isSample();
    }

    /**
     * 테스트케이스가 통과했는지 확인
     */
    public boolean isPassed() {
        return Boolean.TRUE.equals(passed);
    }

    /**
     * 입력 데이터 요약 (긴 입력의 경우 첫 부분만)
     */
    public String getInputDataSummary(int maxLength) {
        if (inputData == null) {
            return "";
        }

        if (inputData.length() <= maxLength) {
            return inputData;
        }

        return inputData.substring(0, maxLength) + "...";
    }

    /**
     * 예상 출력 요약 (긴 출력의 경우 첫 부분만)
     */
    public String getExpectedOutputSummary(int maxLength) {
        if (expectedOutput == null) {
            return "";
        }

        if (expectedOutput.length() <= maxLength) {
            return expectedOutput;
        }

        return expectedOutput.substring(0, maxLength) + "...";
    }

    /**
     * 실제 출력 요약 (긴 출력의 경우 첫 부분만)
     */
    public String getActualOutputSummary(int maxLength) {
        if (actualOutput == null) {
            return "";
        }

        if (actualOutput.length() <= maxLength) {
            return actualOutput;
        }

        return actualOutput.substring(0, maxLength) + "...";
    }

    /**
     * 출력값 비교 (공백, 개행 무시)
     */
    public boolean isOutputMatched() {
        if (expectedOutput == null && actualOutput == null) {
            return true;
        }

        if (expectedOutput == null || actualOutput == null) {
            return false;
        }

        // 공백과 개행을 정규화하여 비교
        String normalizedExpected = normalizeOutput(expectedOutput);
        String normalizedActual = normalizeOutput(actualOutput);

        return normalizedExpected.equals(normalizedActual);
    }

    /**
     * 출력값 정규화 (공백, 개행 처리)
     */
    private String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }

        return output.trim()
                .replaceAll("\\r\\n", "\n")  // CRLF -> LF
                .replaceAll("\\r", "\n")     // CR -> LF
                .replaceAll("[ \\t]+", " ");  // 연속 공백/탭 -> 단일 공백
    }

    /**
     * 테스트케이스 상태 문자열 반환
     */
    public String getStatusString() {
        if (passed == null) {
            return "PENDING";
        }

        return passed ? "PASSED" : "FAILED";
    }

    /**
     * 실행 시간을 사람이 읽기 쉬운 형태로 변환
     */
    public String getFormattedExecutionTime() {
        if (executionTime == null) {
            return "-";
        }

        if (executionTime < 1000) {
            return executionTime + "ms";
        } else {
            return String.format("%.2fs", executionTime / 1000.0);
        }
    }

    /**
     * 메모리 사용량을 사람이 읽기 쉬운 형태로 변환
     */
    public String getFormattedMemoryUsage() {
        if (memoryUsage == null) {
            return "-";
        }

        if (memoryUsage < 1024) {
            return memoryUsage + "KB";
        } else {
            return String.format("%.2fMB", memoryUsage / 1024.0);
        }
    }

    /**
     * 채점 결과 초기화
     */
    public void resetJudgeResult() {
        this.actualOutput = null;
        this.passed = null;
        this.executionTime = null;
        this.memoryUsage = null;
        this.errorMessage = null;
    }

    /**
     * 채점 결과 설정
     */
    public void setJudgeResult(String actualOutput, boolean passed, Long executionTime, Long memoryUsage, String errorMessage) {
        this.actualOutput = actualOutput;
        this.passed = passed;
        this.executionTime = executionTime;
        this.memoryUsage = memoryUsage;
        this.errorMessage = errorMessage;
    }
}