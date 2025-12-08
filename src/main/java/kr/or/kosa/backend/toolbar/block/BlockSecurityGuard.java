package kr.or.kosa.backend.toolbar.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

// 에디터 블록 입력에 대한 보안 검증 전담 클래스
// Freeboard / Codeboard 공통 사용
// "저장 가능한가?" 가 아니라 "서버에 안전한가?"만 판단한다
public class BlockSecurityGuard {

    // 전체 블록 최대 개수 제한
    private static final int MAX_BLOCK_COUNT = 100;

    // 단일 콘텐츠 문자열 최대 길이 제한
    private static final int MAX_CONTENT_LENGTH = 100_000;

    // JSON 최대 깊이 제한 (JSON Bomb 방어)
    private static final int MAX_JSON_DEPTH = 20;

    // 위험한 패턴들 (XSS, JS 실행)
    private static final Pattern[] DANGEROUS_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)<script"),
            Pattern.compile("(?i)</script"),
            Pattern.compile("(?i)javascript:"),
            Pattern.compile("(?i)onerror\\s*="),
            Pattern.compile("(?i)onload\\s*="),
            Pattern.compile("(?i)<iframe"),
            Pattern.compile("(?i)<object"),
            Pattern.compile("(?i)<embed"),
            Pattern.compile("(?i)<link"),
            Pattern.compile("(?i)<style")
    };

    private BlockSecurityGuard() {
        // 유틸리티 클래스
    }

    // 외부에서 사용하는 진입점
    public static void guard(
            List<? extends BlockShape> blocks,
            ObjectMapper objectMapper
    ) {
        // 블록 리스트 존재 여부
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("블록 목록이 비어있습니다.");
        }

        // 블록 개수 제한
        if (blocks.size() > MAX_BLOCK_COUNT) {
            throw new IllegalArgumentException("블록 개수가 너무 많습니다. 최대 허용 개수: " + MAX_BLOCK_COUNT);
        }

        for (BlockShape block : blocks) {
            guardSingleBlock(block, objectMapper);
        }
    }

    // 개별 블록 보안 검증
    private static void guardSingleBlock(
            BlockShape block,
            ObjectMapper objectMapper
    ) {
        // 필수 필드 기본 검증
        if (block.getType() == null || block.getType().isBlank()) {
            throw new IllegalArgumentException("블록 타입이 없습니다.");
        }

        if (block.getContent() == null) {
            throw new IllegalArgumentException("블록 content가 없습니다.");
        }

        BlockType type = BlockType.from(block.getType());

        switch (type) {
            case CODE:
                guardCodeBlock(block.getContent());
                break;

            case TIPTAP:
                guardTiptapBlock(block.getContent(), objectMapper);
                break;
        }
    }

    // 코드 블록 보안 검증
    private static void guardCodeBlock(Object content) {
        if (!(content instanceof String)) {
            throw new IllegalArgumentException("code 블록 content는 문자열이어야 합니다.");
        }

        String code = (String) content;

        // 길이 제한
        if (code.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("코드 블록 길이가 너무 깁니다.");
        }

        // Null byte 공격 방어
        if (code.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("코드 블록에 허용되지 않는 문자가 포함되어 있습니다.");
        }

        // 코드 안에서도 위험 패턴 검사
        detectDangerousPattern(code);
    }

    // Tiptap 블록 보안 검증
    private static void guardTiptapBlock(
            Object content,
            ObjectMapper objectMapper
    ) {
        JsonNode rootNode;

        try {
            rootNode = objectMapper.valueToTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("tiptap content를 JSON으로 변환할 수 없습니다.");
        }

        // JSON 깊이 제한 검사
        checkJsonDepth(rootNode, 0);

        // JSON 전체를 문자열로 변환하여 패턴 검사
        String rawJson = rootNode.toString();

        // 길이 제한
        if (rawJson.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("본문 JSON 크기가 너무 큽니다.");
        }

        // 위험 패턴 검사
        detectDangerousPattern(rawJson);
    }

    // JSON 깊이 검사 (재귀)
    private static void checkJsonDepth(JsonNode node, int depth) {
        if (depth > MAX_JSON_DEPTH) {
            throw new IllegalArgumentException("본문 JSON 구조가 너무 깊습니다.");
        }

        if (node.isContainerNode()) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                checkJsonDepth(it.next(), depth + 1);
            }
        }
    }

    // 위험 패턴 탐지
    private static void detectDangerousPattern(String text) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                throw new IllegalArgumentException("허용되지 않는 스크립트 또는 태그가 포함되어 있습니다.");
            }
        }
    }
}
