package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.domain.ProgrammingLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSolveRequestDto {

    // Eye Tracking 사용 여부
    private Boolean enableEyeTracking;

    // 선호하는 언어 (기본 템플릿 제공용)
    private ProgrammingLanguage preferredLanguage;

    // 난이도 힌트 요청 여부
    private Boolean requestHints;
}