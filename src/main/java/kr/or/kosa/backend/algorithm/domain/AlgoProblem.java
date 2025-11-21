package kr.or.kosa.backend.algorithm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 알고리즘 문제 엔티티
 * 데이터베이스 테이블: ALGO_PROBLEMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgoProblem {

    /**
     * 문제 고유 식별자 (AUTO_INCREMENT)
     */
    private Long algoProblemId;

    /**
     * 문제 제목
     */
    private String algoProblemTitle;

    /**
     * 문제 상세 설명
     */
    private String algoProblemDescription;

    /**
     * 문제 난이도
     */
    private ProblemDifficulty algoProblemDifficulty;

    /**
     * 문제 생성 출처
     */
    private ProblemSource algoProblemSource;

    /**
     * 지원 프로그래밍 언어 (기본값: ALL)
     */
    private String language;

    /**
     * 시간 제한(ms) (기본값: 1000ms)
     */
    private Integer timelimit;

    /**
     * 메모리 제한(MB) (기본값: 256MB)
     */
    private Integer memorylimit;

    /**
     * 문제 생성자 ID
     */
    private Long algoCreater;

    /**
     * 생성 일시
     */
    private LocalDateTime algoCreatedAt;

    /**
     * 수정 일시
     */
    private LocalDateTime algoUpdatedAt;

    /**
     * 문제 태그 (간단히 쉼표로 구분된 문자열로 저장)
     * 예: "DP,그래프,BFS"
     */
    private String algoProblemTags;

    /**
     * 문제 활성화 상태 (1: 활성, 0: 비활성)
     */
    private Boolean algoProblemStatus;

    // === 추가 필드 (연관 데이터) ===

    /**
     * 연관된 테스트케이스 목록 (조인 시 사용)
     */
    private List<AlgoTestcase> testcases;

    /**
     * 문제 통계 정보 (조인 시 사용)
     */
    private Integer totalAttempts;    // 총 시도 횟수
    private Integer successCount;     // 성공 횟수
    private Double averageScore;      // 평균 점수

    // === 편의 메서드 ===

    /**
     * 난이도 배지 색상 반환
     */
    public String getDifficultyColor() {
        if (algoProblemDifficulty == null) return "#gray";

        return switch (algoProblemDifficulty) {
            case BRONZE -> "#cd7f32";
            case SILVER -> "#c0c0c0";
            case GOLD -> "#ffd700";
            case PLATINUM -> "#e5e4e2";
        };
    }

    /**
     * 문제가 AI 생성 문제인지 확인
     */
    public boolean isAIGenerated() {
        return ProblemSource.AI_GENERATED.equals(algoProblemSource);
    }

    /**
     * 문제가 활성 상태인지 확인
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(algoProblemStatus);
    }

    /**
     * 태그를 배열로 변환 (쉼표 구분 문자열 → 리스트)
     */
    public List<String> getTagsAsList() {
        if (algoProblemTags == null || algoProblemTags.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(algoProblemTags.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 태그 리스트를 문자열로 설정 (리스트 → 쉼표 구분 문자열)
     */
    public void setTagsFromList(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            this.algoProblemTags = "";
        } else {
            this.algoProblemTags = String.join(",", tags);
        }
    }

    /**
     * 성공률 계산
     */
    public double getSuccessRate() {
        if (totalAttempts == null || totalAttempts == 0) {
            return 0.0;
        }
        return ((double) (successCount != null ? successCount : 0)) / totalAttempts * 100.0;
    }
}