package kr.or.kosa.backend.algorithm.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * solved.ac API 문제 응답 DTO
 * API: https://solved.ac/api/v3/problem/show
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
public class SolvedAcProblemDto {

    @JsonProperty("problemId")
    private Long problemId;

    @JsonProperty("titleKo")
    private String titleKo;

    @JsonProperty("title")
    private String title;

    @JsonProperty("level")
    private Integer level;  // 1-5: Bronze, 6-10: Silver, 11-15: Gold, 16-20: Platinum

    @JsonProperty("tags")
    private List<TagDto> tags;

    @JsonProperty("isLevelLocked")
    private Boolean isLevelLocked;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagDto {
        @JsonProperty("key")
        private String key;

        @JsonProperty("displayNames")
        private List<DisplayNameDto> displayNames;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DisplayNameDto {
        @JsonProperty("language")
        private String language;

        @JsonProperty("name")
        private String name;

        @JsonProperty("short")
        private String shortName;
    }

    /**
     * 난이도를 DB ENUM 형식으로 변환
     */
    public String getDifficultyEnum() {
        if (level == null) return "BRONZE";
        if (level >= 1 && level <= 5) return "BRONZE";
        if (level >= 6 && level <= 10) return "SILVER";
        if (level >= 11 && level <= 15) return "GOLD";
        if (level >= 16 && level <= 20) return "PLATINUM";
        return "BRONZE";
    }

    /**
     * 한국어 태그 이름 리스트 반환
     */
    public List<String> getKoreanTagNames() {
        return tags.stream()
                .flatMap(tag -> tag.getDisplayNames().stream())
                .filter(dn -> "ko".equals(dn.getLanguage()))
                .map(DisplayNameDto::getName)
                .toList();
    }
}
