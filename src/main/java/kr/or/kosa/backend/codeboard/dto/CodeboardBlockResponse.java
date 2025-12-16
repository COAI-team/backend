package kr.or.kosa.backend.codeboard.dto;

import kr.or.kosa.backend.toolbar.block.BlockShape;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeboardBlockResponse implements BlockShape {
    private String id;
    private String type;
    private Object content;
    private String language;
    private Integer order;
}