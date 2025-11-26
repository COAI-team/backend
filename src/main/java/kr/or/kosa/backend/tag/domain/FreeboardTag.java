package kr.or.kosa.backend.tag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardTag {
    private Long freeboardId;
    private Long tagId;
    private String tagDisplayName;
}
