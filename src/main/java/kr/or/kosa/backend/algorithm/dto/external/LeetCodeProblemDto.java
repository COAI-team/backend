package kr.or.kosa.backend.algorithm.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * alfa-leetcode-api 문제 응답 DTO
 * API: https://alfa-leetcode-api.onrender.com/problems
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
public class LeetCodeProblemDto {

    @JsonProperty("questionId")
    private String questionId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("titleSlug")
    private String titleSlug;

    @JsonProperty("difficulty")
    private String difficulty;  // Easy, Medium, Hard

    @JsonProperty("topicTags")
    private List<TopicTagDto> topicTags;

    @JsonProperty("isPaidOnly")
    private Boolean isPaidOnly;

    @JsonProperty("acRate")
    private Double acRate;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TopicTagDto {
        @JsonProperty("name")
        private String name;

        @JsonProperty("slug")
        private String slug;
    }

    /**
     * 난이도를 DB ENUM 형식으로 변환
     */
    public String getDifficultyEnum() {
        if (difficulty == null) return "BRONZE";
        return switch (difficulty.toUpperCase()) {
            case "EASY" -> "BRONZE";
            case "MEDIUM" -> "SILVER";
            case "HARD" -> "GOLD";
            default -> "BRONZE";
        };
    }

    /**
     * 태그 이름 리스트 반환
     */
    public List<String> getTagNames() {
        if (topicTags == null) return List.of();
        return topicTags.stream()
                .map(TopicTagDto::getName)
                .toList();
    }
}
