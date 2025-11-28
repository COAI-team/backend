package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 샘플 테스트 실행 요청 DTO
 * POST /algo/submissions/test
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestRunRequestDto {

    @NotNull(message = "문제 ID는 필수입니다")
    private Long problemId;

    @NotNull(message = "프로그래밍 언어는 필수입니다")
    private String language;

    @NotNull(message = "소스 코드는 필수입니다")
    private String sourceCode;
}