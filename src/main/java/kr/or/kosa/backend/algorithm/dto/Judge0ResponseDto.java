package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Judge0 API 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Judge0ResponseDto {

    private String token;
    private StatusDto status;
    private String stdout;
    private String stderr;
    private String compile_output;
    private String message;
    private Double time;
    private Double memory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusDto {
        private Integer id;
        private String description;
    }

    // Judge0 상태 코드 매핑
    public AlgoSubmission.JudgeResult toJudgeResult() {
        if (status == null || status.getId() == null) {
            return AlgoSubmission.JudgeResult.PENDING;
        }

        switch (status.getId()) {
            case 1: // In Queue
            case 2: // Processing
                return AlgoSubmission.JudgeResult.PENDING;
            case 3: // Accepted
                return AlgoSubmission.JudgeResult.AC;
            case 4: // Wrong Answer
                return AlgoSubmission.JudgeResult.WA;
            case 5: // Time Limit Exceeded
                return AlgoSubmission.JudgeResult.TLE;
            case 6: // Compilation Error
                return AlgoSubmission.JudgeResult.CE;
            case 7: // SIGSEGV
            case 8: // SIGXFSZ
            case 9: // SIGFPE
            case 10: // SIGABRT
            case 11: // NZEC
            case 12: // Other
                return AlgoSubmission.JudgeResult.RE;
            case 13: // Internal Error
            case 14: // Exec Format Error
                return AlgoSubmission.JudgeResult.RE;
            default:
                return AlgoSubmission.JudgeResult.PENDING;
        }
    }
}
