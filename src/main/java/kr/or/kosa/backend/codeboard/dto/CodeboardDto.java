package kr.or.kosa.backend.codeboard.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.toolbar.block.BlockJsonConverter;
import kr.or.kosa.backend.toolbar.block.BlockShape;
import kr.or.kosa.backend.toolbar.block.BlockTextExtractor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private List<CodeboardBlockResponse> blocks;
    private Long codeboardClick;
    private LocalDateTime codeboardCreatedAt;
    private List<String> tags;

    // blocks를 JSON 문자열로 변환
    public String toJsonContent(ObjectMapper objectMapper) throws Exception {
        List<BlockShape> blockList = BlockJsonConverter.toBlockList(blocks, objectMapper);
        return objectMapper.writeValueAsString(blockList);
    }

    // 순수 텍스트 추출 (검색용)
    public String toPlainText(ObjectMapper objectMapper) throws Exception {
        List<BlockShape> blockList = BlockJsonConverter.toBlockList(blocks, objectMapper);
        return BlockTextExtractor.extractPlainText(blockList, objectMapper);
    }
}