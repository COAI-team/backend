package kr.or.kosa.backend.tag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagAutocompleteDto {
    private Long tagId;
    private String tagDisplayName;
    private Long count;
}
