package kr.or.kosa.backend.toolbar.block;

import java.util.Arrays;

// 지원하는 블록 타입 정의
public enum BlockType {

    TIPTAP("tiptap"),
    CODE("code");

    private final String value;

    BlockType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    // 문자열 → BlockType 변환
    public static BlockType from(String type) {
        return Arrays.stream(values())
                .filter(t -> t.value.equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("지원하지 않는 블록 타입입니다: " + type));
    }

    // 유효한 타입인지 여부 확인
    public static boolean isValid(String type) {
        return Arrays.stream(values())
                .anyMatch(t -> t.value.equalsIgnoreCase(type));
    }
}
