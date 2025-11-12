package kr.or.kosa.backend.tag.dto;

import lombok.Data;

@Data
public class TagDTO {
    private Long tagId;
    private String tagName;
    private Long freeboardId;
    private Long codeboardId;
}
