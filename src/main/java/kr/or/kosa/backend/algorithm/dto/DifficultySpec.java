package kr.or.kosa.backend.algorithm.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Phase 1: 난이도별 알고리즘 스펙 정의
 *
 * 각 알고리즘 주제에 대해 난이도별로 적용할 제약 조건을 정의합니다:
 * - 입력 크기 (inputSize)
 * - 시간/메모리 제한
 * - 기대 시간복잡도
 * - 문제 설명
 */
@Getter
@Setter
public class DifficultySpec {

    private String inputSize;
    private int timeLimit;
    private int memoryLimit;
    private String timeComplexity;
    private String description;

    public DifficultySpec() {
    }

    public DifficultySpec(String inputSize, int timeLimit, int memoryLimit,
                          String timeComplexity, String description) {
        this.inputSize = inputSize;
        this.timeLimit = timeLimit;
        this.memoryLimit = memoryLimit;
        this.timeComplexity = timeComplexity;
        this.description = description;
    }

    /**
     * 기본 스펙 생성 (프로필에 정의되지 않은 경우 사용)
     */
    public static DifficultySpec defaultSpec(String difficulty) {
        return switch (difficulty.toUpperCase()) {
            case "BRONZE" -> new DifficultySpec("N <= 1,000", 1000, 256, "O(N^2)", "기본 문제");
            case "SILVER" -> new DifficultySpec("N <= 10,000", 1000, 256, "O(N log N)", "중급 문제");
            case "GOLD" -> new DifficultySpec("N <= 100,000", 2000, 512, "O(N log N)", "고급 문제");
            case "PLATINUM" -> new DifficultySpec("N <= 500,000", 3000, 1024, "O(N log N)", "최고급 문제");
            default -> new DifficultySpec("N <= 10,000", 1000, 256, "O(N log N)", "표준 문제");
        };
    }
}
