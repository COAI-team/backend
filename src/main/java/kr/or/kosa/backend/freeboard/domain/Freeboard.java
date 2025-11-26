package kr.or.kosa.backend.freeboard.domain;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Freeboard {
    private Long freeboardId;
    private Long userId;
    private String freeboardTitle;
    private String freeboardContent;     // HTML 형식의 전체 콘텐츠 <h1>제목</h1>
    private String freeboardPlainText;   // HTML 태그를 제거한 순수 텍스트, 검색, 미리보기에 사용
    private Long freeboardClick;
    private String freeboardImage;
    private String freeboardRepresentImage;
    private LocalDateTime freeboardCreatedAt;
    private String freeboardDeletedYn;

    @Setter // tags에만 Setter 추가
    private List<String> tags;

}
