package kr.or.kosa.backend.freeboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조회(응답) 시에 사용되는 블록 DTO
 * tiptap / code 블록 공통 구조
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardBlockResponse {

    private String id;
    private String type;     // "tiptap" or "code"
    private Object content;  // tiptap JSON or code string
    private String language; // code block일 때 사용
    private Integer order;
}
