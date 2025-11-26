package kr.or.kosa.backend.freeboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.or.kosa.backend.tag.validation.ValidTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다.")
    private String freeboardTitle;

    @NotNull(message = "내용은 필수입니다.")
    @Valid
    private List<BlockDto> blocks;

    @Size(max = 500, message = "대표 이미지 URL은 500자를 초과할 수 없습니다.")
    private String freeboardRepresentImage;

    @Size(max = 10, message = "태그는 최대 10개까지 가능합니다.")
    private List<@ValidTag String> tags;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlockDto {

        @NotBlank(message = "블록 ID는 필수입니다.")
        private String id;

        @NotBlank(message = "블록 타입은 필수입니다.")
        private String type;

        @NotNull(message = "블록 내용은 필수입니다.")
        private Object content;

        private String language;

        @NotNull(message = "블록 순서는 필수입니다.")
        private Integer order;
    }

    public FreeboardDto toDto() {
        return FreeboardDto.builder()
                .freeboardTitle(this.freeboardTitle)
                .blocks(this.blocks.stream()
                        .map(block -> FreeboardBlockResponse.builder()
                                .id(block.getId())
                                .type(block.getType())
                                .content(block.getContent())
                                .language(block.getLanguage())
                                .order(block.getOrder())
                                .build())
                        .toList())
                .freeboardRepresentImage(this.freeboardRepresentImage)
                .tags(this.tags)
                .build();
    }
}