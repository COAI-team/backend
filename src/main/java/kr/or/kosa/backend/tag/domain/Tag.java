package kr.or.kosa.backend.tag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {
    private Long tagId;
    private String tagName;
    private String popularDisplayName;
    private Long usageCount;
}
