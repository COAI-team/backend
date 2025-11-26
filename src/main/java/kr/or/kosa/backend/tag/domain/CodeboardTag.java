package kr.or.kosa.backend.tag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeboardTag {
    private Long codeboardId;
    private Long tagId;
    private String tagDisplayName;
}
