package kr.or.kosa.backend.algorithm.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/**
 * 샘플 테스트 실행 요청 DTO
 * POST /algo/submissions/test
 *
 * Request DTO: JSON 역직렬화용
 * - @NoArgsConstructor: Jackson이 기본 생성자로 객체 생성
 * - @Setter: Jackson이 값 주입
 * - @Getter: 서비스에서 값 읽기
 */
@Getter
@Setter
@NoArgsConstructor
public class TestRunRequestDto {

    @NotNull(message = "문제 ID는 필수입니다")
    private Long problemId;

    @NotNull(message = "프로그래밍 언어는 필수입니다")
    private String language;

    @NotNull(message = "소스 코드는 필수입니다")
    private String sourceCode;
}