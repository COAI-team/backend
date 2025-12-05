package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Judge0 API 응답 DTO
 *
 * 외부 API 수신용 DTO: JSON 역직렬화
 * - @NoArgsConstructor: Jackson이 기본 생성자로 객체 생성
 * - @Setter: Jackson이 값 주입
 * - @Getter: 서비스에서 값 읽기
 */
@Getter
@Setter
@NoArgsConstructor
public class Judge0ResponseDto {

    private String token;
    private StatusDto status;
    private String stdout;
    private String stderr;
    private String compile_output;
    private String message;
    private Double time;
    private Double memory;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StatusDto {
        private Integer id;
        private String description;
    }

    // Judge0 상태 코드 매핑
    public JudgeResult toJudgeResult() {
        if (status == null || status.getId() == null) {
            return JudgeResult.PENDING;
        }

        switch (status.getId()) {
            case 1: // In Queue
            case 2: // Processing
                return JudgeResult.PENDING;
            case 3: // Accepted
                return JudgeResult.AC;
            case 4: // Wrong Answer
                return JudgeResult.WA;
            case 5: // Time Limit Exceeded
                return JudgeResult.TLE;
            case 6: // Compilation Error
                return JudgeResult.CE;
            case 7: // SIGSEGV
            case 8: // SIGXFSZ
            case 9: // SIGFPE
            case 10: // SIGABRT
            case 11: // NZEC
            case 12: // Other
                return JudgeResult.RE;
            case 13: // Internal Error
            case 14: // Exec Format Error
                return JudgeResult.RE;
            default:
                return JudgeResult.PENDING;
        }
    }
}
