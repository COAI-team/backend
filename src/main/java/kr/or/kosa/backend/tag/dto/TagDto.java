package kr.or.kosa.backend.tag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagDto {
    private Long tagId;
    private String tagName;
    private String tagDisplayName;
}
