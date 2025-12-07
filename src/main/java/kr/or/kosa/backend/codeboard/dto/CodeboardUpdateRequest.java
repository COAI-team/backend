package kr.or.kosa.backend.codeboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeboardUpdateRequest {

    @NotNull(message = "분석 ID는 필수입니다.")
    private String analysisId;

    @NotBlank(message = "제목은 필수입니다.")
    private String codeboardTitle;

    @NotNull(message = "블록 내용은 필수입니다.")
    private Object blocks;

    private List<String> tags;

    public CodeboardDto toDto() {
        return CodeboardDto.builder()
                .analysisId(analysisId)
                .codeboardTitle(codeboardTitle)
                .blocks(blocks)
                .tags(tags)
                .build();
    }
}