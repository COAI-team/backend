package kr.or.kosa.backend.freeboard.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.toolbar.block.BlockSecurityGuard;
import kr.or.kosa.backend.toolbar.block.BlockShape;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        if (blocks == null || blocks.isEmpty()) {
            return "[]";
        }

        // 보안 검증
        List<BlockShape> blockList = new ArrayList<>(blocks);
        BlockSecurityGuard.guard(blockList, objectMapper);

        return objectMapper.writeValueAsString(blocks);
    }

    // 순수 텍스트 추출 (검색용)
    public String toPlainText(ObjectMapper objectMapper) {
        StringBuilder text = new StringBuilder();

        if (blocks != null) {
            for (FreeboardBlockResponse block : blocks) {
                if ("tiptap".equals(block.getType())) {
                    String tiptapText = extractFromTiptap(block.getContent(), objectMapper);
                    text.append(tiptapText).append("\n");
                } else if ("code".equals(block.getType())) {
                    text.append(block.getContent()).append("\n");
                }
            }
        }

        return text.toString().trim();
    }

    // Tiptap JSON에서 텍스트 추출
    private String extractFromTiptap(Object content, ObjectMapper objectMapper) {
        try {
            // content가 이미 String(HTML)인 경우
            if (content instanceof String) {
                String htmlContent = (String) content;
                // HTML 태그 제거
                return htmlContent
                        .replaceAll("<[^>]+>", " ")  // 모든 HTML 태그 제거
                        .replaceAll("\\s+", " ")      // 연속된 공백을 하나로
                        .trim();
            }

            // content가 JSON 객체인 경우
            JsonNode node = objectMapper.valueToTree(content);
            return extractTextFromNode(node);
        } catch (Exception e) {
            log.warn("Tiptap 텍스트 추출 실패", e);
            return "";
        }
    }

    // JSON 노드에서 재귀적으로 텍스트 추출
    private String extractTextFromNode(JsonNode node) {
        StringBuilder text = new StringBuilder();

        if (node.has("text")) {
            text.append(node.get("text").asText()).append(" ");
        }

        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                text.append(extractTextFromNode(child));
            }
        }

        return text.toString();
    }
}