package kr.or.kosa.backend.freeboard.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.toolbar.block.BlockJsonConverter;
import kr.or.kosa.backend.toolbar.block.BlockSecurityGuard;
import kr.or.kosa.backend.toolbar.block.BlockShape;
import kr.or.kosa.backend.toolbar.block.BlockTextExtractor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardDto {
    private Long freeboardId;
    private Long userId;
    private String freeboardTitle;
    private List<FreeboardBlockResponse> blocks;
    private String freeboardRepresentImage;
    private Long freeboardClick;
    private LocalDateTime freeboardCreatedAt;
    private List<String> tags;

    // blocks를 JSON 문자열로 변환 (보안 검증 포함)
    public String toJsonContent(ObjectMapper objectMapper) throws Exception {
        // 블록 변환
        List<BlockShape> blockList = BlockJsonConverter.toBlockList(blocks, objectMapper);

        // 보안 검증
        BlockSecurityGuard.guard(blockList, objectMapper);

        return objectMapper.writeValueAsString(blockList);
    }

    // 순수 텍스트 추출 (검색용)
    public String toPlainText(ObjectMapper objectMapper) throws Exception {
        List<BlockShape> blockList = BlockJsonConverter.toBlockList(blocks, objectMapper);
        return BlockTextExtractor.extractPlainText(blockList, objectMapper);
    }
}