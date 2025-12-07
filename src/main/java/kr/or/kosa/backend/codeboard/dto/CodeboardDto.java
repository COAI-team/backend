package kr.or.kosa.backend.codeboard.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.toolbar.block.BlockJsonConverter;
import kr.or.kosa.backend.toolbar.block.BlockShape;
import kr.or.kosa.backend.toolbar.block.BlockTextExtractor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeboardDto {
    private Long codeboardId;
    private Long userId;
    private String analysisId;
    private String codeboardTitle;
    private Object blocks;
    private List<String> tags;

    public String toJsonContent(ObjectMapper objectMapper) throws Exception {
        List<BlockShape> blockList = BlockJsonConverter.toBlockList(blocks, objectMapper);
        return objectMapper.writeValueAsString(blockList);
    }

    public String toPlainText(ObjectMapper objectMapper) throws Exception {
        List<BlockShape> blockList = BlockJsonConverter.toBlockList(blocks, objectMapper);
        return BlockTextExtractor.extractPlainText(codeboardTitle, blockList, objectMapper);
    }
}