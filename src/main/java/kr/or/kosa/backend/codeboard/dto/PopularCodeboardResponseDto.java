package kr.or.kosa.backend.codeboard.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularCodeboardResponseDto {
    private Long codeboardId;
    private Long userId;
    private String userNickname;
    private String userImage;
    private String codeboardTitle;
    private String codeboardPlainText;
    private Long codeboardClick;
    private LocalDateTime codeboardCreatedAt;
    private Integer aiScore;  // 코드 스멜 점수
    private Integer likeCount;
    private Integer commentCount;
    private Integer popularityScore;
    private Integer ranking;
}