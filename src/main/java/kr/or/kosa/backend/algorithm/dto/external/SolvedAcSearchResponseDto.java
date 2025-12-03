package kr.or.kosa.backend.algorithm.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * solved.ac API 검색 응답 DTO
 * API: https://solved.ac/api/v3/search/problem
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolvedAcSearchResponseDto {

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("items")
    private List<SolvedAcProblemDto> items;
}
