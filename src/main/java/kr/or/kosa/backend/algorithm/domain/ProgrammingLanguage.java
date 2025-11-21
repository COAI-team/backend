package kr.or.kosa.backend.algorithm.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로그래밍 언어 Enum
 * Judge0 API와 Monaco Editor를 고려한 설계
 */
@Getter
@RequiredArgsConstructor
public enum ProgrammingLanguage {

    JAVA("java", "Java", 62, ".java", "// Java 코드를 작성하세요\npublic class Solution {\n    public static void main(String[] args) {\n        \n    }\n}"),
    PYTHON("python", "Python", 71, ".py", "# Python 코드를 작성하세요\ndef solution():\n    pass\n\nif __name__ == \"__main__\":\n    solution()"),
    CPP("cpp", "C++", 54, ".cpp", "// C++ 코드를 작성하세요\n#include <iostream>\nusing namespace std;\n\nint main() {\n    \n    return 0;\n}"),
    C("c", "C", 50, ".c", "// C 코드를 작성하세요\n#include <stdio.h>\n\nint main() {\n    \n    return 0;\n}"),
    JAVASCRIPT("javascript", "JavaScript", 63, ".js", "// JavaScript 코드를 작성하세요\nfunction solution() {\n    \n}\n\nsolution();"),
    GOLANG("go", "Go", 60, ".go", "// Go 코드를 작성하세요\npackage main\n\nimport \"fmt\"\n\nfunc main() {\n    \n}"),
    KOTLIN("kotlin", "Kotlin", 78, ".kt", "// Kotlin 코드를 작성하세요\nfun main() {\n    \n}"),
    RUST("rust", "Rust", 73, ".rs", "// Rust 코드를 작성하세요\nfn main() {\n    \n}"),
    SWIFT("swift", "Swift", 83, ".swift", "// Swift 코드를 작성하세요\nimport Foundation\n\n"),
    CSHARP("csharp", "C#", 51, ".cs", "// C# 코드를 작성하세요\nusing System;\n\nclass Program {\n    static void Main() {\n        \n    }\n}");

    /**
     * Monaco Editor에서 사용하는 언어 식별자
     */
    private final String monacoId;

    /**
     * 사용자에게 표시되는 언어명
     */
    private final String displayName;

    /**
     * Judge0 API 언어 ID
     */
    private final int judge0Id;

    /**
     * 파일 확장자
     */
    private final String fileExtension;

    /**
     * 기본 코드 템플릿
     */
    private final String defaultTemplate;

    /**
     * 모든 지원 언어 반환
     */
    public static ProgrammingLanguage[] getAllSupported() {
        return values();
    }

    /**
     * Monaco ID로 언어 찾기
     */
    public static ProgrammingLanguage fromMonacoId(String monacoId) {
        if (monacoId == null) {
            return null;
        }

        for (ProgrammingLanguage lang : values()) {
            if (lang.monacoId.equalsIgnoreCase(monacoId)) {
                return lang;
            }
        }

        throw new IllegalArgumentException("Unknown Monaco language: " + monacoId);
    }

    /**
     * Judge0 ID로 언어 찾기
     */
    public static ProgrammingLanguage fromJudge0Id(int judge0Id) {
        for (ProgrammingLanguage lang : values()) {
            if (lang.judge0Id == judge0Id) {
                return lang;
            }
        }

        throw new IllegalArgumentException("Unknown Judge0 language ID: " + judge0Id);
    }

    /**
     * 표시명으로 언어 찾기
     */
    public static ProgrammingLanguage fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        for (ProgrammingLanguage lang : values()) {
            if (lang.displayName.equalsIgnoreCase(displayName)) {
                return lang;
            }
        }

        throw new IllegalArgumentException("Unknown language display name: " + displayName);
    }

    /**
     * 컴파일 언어인지 확인
     */
    public boolean isCompiled() {
        return switch (this) {
            case JAVA, CPP, C, GOLANG, KOTLIN, RUST, SWIFT, CSHARP -> true;
            case PYTHON, JAVASCRIPT -> false;
        };
    }

    /**
     * 인터프리터 언어인지 확인
     */
    public boolean isInterpreted() {
        return !isCompiled();
    }

    /**
     * 객체 지향 언어인지 확인
     */
    public boolean isObjectOriented() {
        return switch (this) {
            case JAVA, CPP, KOTLIN, SWIFT, CSHARP -> true;
            case PYTHON, JAVASCRIPT -> true;  // 멀티 패러다임
            case C, GOLANG, RUST -> false;
        };
    }

    /**
     * 메모리 관리가 자동인지 확인 (GC 등)
     */
    public boolean hasAutomaticMemoryManagement() {
        return switch (this) {
            case JAVA, PYTHON, JAVASCRIPT, GOLANG, KOTLIN, SWIFT, CSHARP -> true;
            case CPP, C, RUST -> false;
        };
    }

    /**
     * 파일명 생성 (예: Solution.java, solution.py)
     */
    public String generateFileName(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "Solution";
        }

        // Java와 C#은 대문자로 시작
        if (this == JAVA || this == CSHARP || this == KOTLIN || this == SWIFT) {
            baseName = Character.toUpperCase(baseName.charAt(0)) + baseName.substring(1);
        } else {
            baseName = baseName.toLowerCase();
        }

        return baseName + fileExtension;
    }

    /**
     * UI에서 사용할 색상 반환
     */
    public String getBrandColor() {
        return switch (this) {
            case JAVA -> "#f89820";
            case PYTHON -> "#3776ab";
            case CPP -> "#00599c";
            case C -> "#a8b9cc";
            case JAVASCRIPT -> "#f7df1e";
            case GOLANG -> "#00add8";
            case KOTLIN -> "#7f52ff";
            case RUST -> "#000000";
            case SWIFT -> "#fa7343";
            case CSHARP -> "#239120";
        };
    }
}