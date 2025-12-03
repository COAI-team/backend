package kr.or.kosa.backend.algorithm.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * alfa-leetcode-api 문제 목록 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeetCodeProblemsResponseDto {

    @JsonProperty("problemsetQuestionList")
    private List<LeetCodeProblemDto> problemsetQuestionList;

    @JsonProperty("totalQuestions")
    private Integer totalQuestions;
}
