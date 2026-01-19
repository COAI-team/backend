package kr.or.kosa.backend.codeboard.domain;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Codeboard {
    private Long codeboardId;
    private Long userId;
    private String analysisId;
    private String codeboardTitle;
    private String codeboardBlocks;    // JSON 문자열 (List<BlockShape> 직렬화)
    private String codeboardPlainText; // 검색용 순수 텍스트 (BlockTextExtractor로 추출)
    private Long codeboardClick;
    private LocalDateTime codeboardCreatedAt;
    private String codeboardDeletedYn;

    @Setter
    private List<String> tags;
}