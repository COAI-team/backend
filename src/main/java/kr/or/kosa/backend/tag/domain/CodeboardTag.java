package kr.or.kosa.backend.tag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeboardTag {
    private Long codeboardId;
    private Long tagId;
    private String tagDisplayName;
}