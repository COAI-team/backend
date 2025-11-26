package kr.or.kosa.backend.codeboard.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Codeboard {
    private Long codeboardId;
    private Long userId;
    private String codeboardTitle;
    private String codeContent;       // 코드 그 자체(미리보기/분석용)
    private String codePlainText;     // 태그/요약용
    private LocalDateTime createdAt;
    private Long clickCount;
    private String deletedYn;
}
