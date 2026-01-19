package kr.or.kosa.backend.freeboard.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularFreeboard {
    private Long weeklyPopularFreeboardId;
    private Long freeboardId;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer popularityScore;
    private Integer ranking;
    private LocalDateTime createdAt;
}