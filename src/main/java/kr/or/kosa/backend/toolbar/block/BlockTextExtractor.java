package kr.or.kosa.backend.toolbar.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

// 블록 목록에서 순수 텍스트 추출
// 검색 / 미리보기 용도
public class BlockTextExtractor {

    private BlockTextExtractor() {
        // 유틸리티 클래스
    }

    // 전체 블록에서 plain text 추출
    public static String extractPlainText(
            String title,
            List<? extends BlockShape> blocks,
            ObjectMapper objectMapper
    ) {
        StringBuilder text = new StringBuilder();

        // 제목 추가
        if (title != null && !title.isBlank()) {
            text.append(title).append("\n\n");
        }

        if (blocks == null) {
            return text.toString().trim();
        }

        for (BlockShape block : blocks) {
            BlockType type = BlockType.from(block.getType());

            switch (type) {
                case TIPTAP:
                    text.append(extractFromTiptap(block.getContent(), objectMapper))
                            .append("\n");
                    break;

                case CODE:
                    // 코드 블록은 기본적으로 제외
                    // 필요하면 여기서 주석 추출 등으로 확장 가능
                    break;
            }
        }

        return text.toString().trim();
    }

    // Tiptap JSON 객체에서 텍스트 추출
    private static String extractFromTiptap(Object content, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.valueToTree(content);
            return extractTextRecursive(node);
        } catch (Exception e) {
            // JSON 구조가 깨져도 서비스가 죽지 않도록 보호
            return "";
        }
    }

    // JSON 노드 재귀 탐색
    private static String extractTextRecursive(JsonNode node) {
        StringBuilder result = new StringBuilder();

        if (node.has("text")) {
            result.append(node.get("text").asText()).append(" ");
        }

        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                result.append(extractTextRecursive(child));
            }
        }

        return result.toString();
    }
}
