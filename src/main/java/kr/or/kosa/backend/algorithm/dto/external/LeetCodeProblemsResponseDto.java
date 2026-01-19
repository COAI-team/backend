package kr.or.kosa.backend.algorithm.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * alfa-leetcode-api 문제 목록 응답 DTO
 *
 * 외부 API 수신용 DTO: JSON 역직렬화
 * - @NoArgsConstructor: Jackson이 기본 생성자로 객체 생성
 * - @Setter: Jackson이 값 주입
 * - @Getter: 서비스에서 값 읽기
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeetCodeProblemsResponseDto {

    @JsonProperty("problemsetQuestionList")
    private List<LeetCodeProblemDto> problemsetQuestionList;

    @JsonProperty("totalQuestions")
    private Integer totalQuestions;
}
