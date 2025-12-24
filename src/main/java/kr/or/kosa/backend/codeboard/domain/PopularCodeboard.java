package kr.or.kosa.backend.codeboard.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularCodeboard {
    private Long weeklyPopularCodeboardId;
    private Long codeboardId;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer popularityScore;
    private Integer ranking;
    private LocalDateTime createdAt;
}