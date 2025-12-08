package kr.or.kosa.backend.toolbar.block;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockShapeImpl implements BlockShape {

    private String id;
    private String type;
    private Object content;
    private String language;
    private Integer order;
}