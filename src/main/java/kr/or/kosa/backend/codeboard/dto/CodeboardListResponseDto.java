package kr.or.kosa.backend.codeboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeboardListResponseDto {
    private Long codeboardId;
    private Long userId;
    private String userNickname;
    private String analysisId;
    private String codeboardTitle;
    private String codeboardSummary;
    private Long codeboardClick;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime codeboardCreatedAt;
    private String codeboardTag;
    private Integer aiScore; // 냄새 태그
}