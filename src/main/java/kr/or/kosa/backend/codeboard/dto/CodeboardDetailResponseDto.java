package kr.or.kosa.backend.codeboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeboardDetailResponseDto {
    private Long codeboardId;
    private Long userId;
    private String userNickname;
    private String analysisId;
    private String codeboardTitle;
    private String codeboardContent;
    private Long codeboardClick;
    private Integer likeCount;
    private LocalDateTime codeboardCreatedAt;
    private List<String> tags;
    private boolean isLiked;
}