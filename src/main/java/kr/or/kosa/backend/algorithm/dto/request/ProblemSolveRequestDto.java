package kr.or.kosa.backend.algorithm.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문제 풀이 시작 요청 DTO
 *
 * Request DTO: JSON 역직렬화용
 * - @NoArgsConstructor: Jackson이 기본 생성자로 객체 생성
 * - @Setter: Jackson이 값 주입
 * - @Getter: 서비스에서 값 읽기
 */
@Getter
@Setter
@NoArgsConstructor
public class ProblemSolveRequestDto {

    // Eye Tracking 사용 여부
    private Boolean enableEyeTracking;

    // 선호하는 언어 (기본 템플릿 제공용) - DB 언어명
    private String preferredLanguage;

    // 난이도 힌트 요청 여부
    private Boolean requestHints;
}