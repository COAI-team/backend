package kr.or.kosa.backend.algorithm.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AlgoErrorCode implements ErrorCode {

    // ================================================================
    // ✅ 공통 오류
    // ================================================================
    INVALID_INPUT("ALGO_4001", "요청 데이터가 유효하지 않습니다."),
    UNAUTHORIZED("ALGO_4002", "해당 작업을 수행할 권한이 없습니다."),

    // ================================================================
    // ✅ 문제(Problem) 관련 오류
    // ================================================================
    PROBLEM_NOT_FOUND("ALGO_4100", "해당 문제를 찾을 수 없습니다."),
    PROBLEM_SAVE_FAIL("ALGO_4101", "문제 저장 중 오류가 발생했습니다."),
    PROBLEM_GENERATION_FAIL("ALGO_4102", "AI 문제 생성에 실패했습니다."),

    // ================================================================
    // ✅ 테스트 실행(TestRun) 관련 오류
    // ================================================================
    TEST_RUN_FAILED("ALGO_4200", "테스트 실행 중 오류가 발생했습니다."),
    INVALID_LANGUAGE("ALGO_4201", "지원하지 않는 언어입니다."),
    INVALID_TESTCASE("ALGO_4202", "유효하지 않은 테스트케이스입니다."),

    // ================================================================
    // ✅ 제출(Submission) 관련 오류
    // ================================================================
    SUBMISSION_NOT_FOUND("ALGO_4300", "해당 제출을 찾을 수 없습니다."),
    SUBMISSION_SAVE_FAIL("ALGO_4301", "제출 저장 중 오류가 발생했습니다."),
    SUBMISSION_UPDATE_FAIL("ALGO_4302", "제출 상태를 업데이트할 수 없습니다."),

    // ================================================================
    // ✅ 평가(Evaluation) 관련 오류
    // ================================================================
    EVALUATION_NOT_FOUND("ALGO_4400", "해당 평가 정보를 찾을 수 없습니다."),
    EVALUATION_RETRY_FAIL("ALGO_4401", "AI 평가 재실행 중 오류가 발생했습니다."),
    EVALUATION_PROCESSING_ERROR("ALGO_4402", "평가 처리 중 예외가 발생했습니다.");

    // ================================================================
    // Fields + Getter
    // ================================================================
    private final String code;
    private final String message;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
