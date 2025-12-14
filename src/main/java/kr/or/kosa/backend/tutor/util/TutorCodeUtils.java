package kr.or.kosa.backend.tutor.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TutorCodeUtils {

    private static final Pattern LINE_COMMENT = Pattern.compile("//.*");
    private static final Pattern HASH_COMMENT = Pattern.compile("#.*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private TutorCodeUtils() {
    }

    /**
     * 코드에서 공백/빈줄/주석/사소한 세미콜론 변경을 제거해 의미 있는 해시를 만든다.
     */
    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String withoutBlock = BLOCK_COMMENT.matcher(code).replaceAll("");
        String[] lines = withoutBlock.split("\\R");
        List<String> cleaned = Arrays.stream(lines)
                .map(line -> LINE_COMMENT.matcher(line).replaceAll(""))
                .map(line -> HASH_COMMENT.matcher(line).replaceAll(""))
                .map(String::trim)
                .map(line -> line.replace(";", "")) // 세미콜론만 추가/삭제는 동일 취급
                .filter(l -> !l.isEmpty())
                .collect(Collectors.toList());
        return String.join("\n", cleaned);
    }

    /**
     * 코드가 너무 길 때 LLM/Judge0에 전달할 샘플로 앞/뒤만 남기고 중간을 중략 처리한다.
     */
    public static String trimLongCode(String code, int maxLines, int headKeep, int tailKeep) {
        if (code == null) {
            return "";
        }
        String[] lines = code.split("\\R", -1);
        if (lines.length <= maxLines) {
            return code;
        }
        String head = String.join("\n", Arrays.copyOfRange(lines, 0, Math.min(headKeep, lines.length)));
        String tail = String.join("\n", Arrays.copyOfRange(lines, Math.max(lines.length - tailKeep, 0), lines.length));
        return head + "\n// ... 중략 (코드 일부는 길이 제한으로 생략되었습니다)\n" + tail;
    }
}
