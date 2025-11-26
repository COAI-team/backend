package kr.or.kosa.backend.freeboard.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    // blocks를 JSON 문자열로 변환
    public String toJsonContent(ObjectMapper objectMapper) throws Exception {
        return objectMapper.writeValueAsString(this.blocks);
    }

    // 순수 텍스트 추출 (검색/RAG용)
    public String toPlainText(ObjectMapper objectMapper) {
        StringBuilder text = new StringBuilder();

        // 제목 추가
        if (freeboardTitle != null) {
            text.append(freeboardTitle).append("\n\n");
        }

        // 각 블록에서 텍스트 추출
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
            JsonNode node = objectMapper.valueToTree(content);
            return extractTextFromNode(node);
        } catch (Exception e) {
            log.warn("Tiptap 텍스트 추출 실패", e);  // 로깅 추가
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
