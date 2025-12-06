package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.AlgoSubmissionDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemSource;
import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO 관련 유틸리티 메서드
 * 기존 Entity의 비즈니스 로직을 Service 레이어로 이동
 */
@Component
public class AlgorithmDtoUtils {

    // ==================== AlgoProblem 관련 ====================

    /**
     * 난이도 배지 색상 반환
     */
    public String getDifficultyColor(ProblemDifficulty difficulty) {
        if (difficulty == null) {
            return "#gray";
        }
        return difficulty.getColor();
    }

    /**
     * 문제가 AI 생성 문제인지 확인
     */
    public boolean isAIGenerated(AlgoProblemDto problem) {
        return ProblemSource.AI_GENERATED.equals(problem.getAlgoProblemSource());
    }

    /**
     * 문제가 활성 상태인지 확인
     */
    public boolean isActive(AlgoProblemDto problem) {
        return Boolean.TRUE.equals(problem.getAlgoProblemStatus());
    }

    /**
     * 태그를 배열로 변환
     * JSON 배열 형식("[\"수학\", \"구현\"]") 또는 쉼표 구분 문자열("수학,구현") 모두 지원
     */
    public List<String> getTagsAsList(String algoProblemTags) {
        if (algoProblemTags == null || algoProblemTags.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String trimmed = algoProblemTags.trim();

        // JSON 배열 형식인 경우 (예: "[\"수학\", \"구현\"]")
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                String content = trimmed.substring(1, trimmed.length() - 1);
                return Arrays.stream(content.split(","))
                        .map(String::trim)
                        .map(s -> s.replaceAll("^\"|\"$", ""))
                        .filter(tag -> !tag.isEmpty())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        // 쉼표 구분 문자열인 경우
        return Arrays.asList(trimmed.split(","))
                .stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 태그 리스트를 문자열로 변환
     */
    public String tagsToString(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return String.join(",", tags);
    }

    /**
     * 성공률 계산
     */
    public double calculateSuccessRate(Integer totalAttempts, Integer successCount) {
        if (totalAttempts == null || totalAttempts == 0) {
            return 0.0;
        }
        return ((double) (successCount != null ? successCount : 0)) / totalAttempts * 100.0;
    }

    // ==================== AlgoSubmission 관련 ====================

    /**
     * 채점이 완료되었는지 확인
     */
    public boolean isJudgingCompleted(AlgoSubmissionDto submission) {
        return submission.getJudgeResult() != null && submission.getJudgeResult() != JudgeResult.PENDING;
    }

    /**
     * 정답 여부 확인
     */
    public boolean isAccepted(AlgoSubmissionDto submission) {
        return JudgeResult.AC.equals(submission.getJudgeResult());
    }

    /**
     * 풀이 시간 계산 (분)
     */
    public Integer getSolvingDurationMinutes(Integer solvingDurationSeconds) {
        return solvingDurationSeconds != null ? solvingDurationSeconds / 60 : null;
    }

    /**
     * 테스트케이스 통과율 계산
     */
    public Double getTestPassRate(Integer passedTestCount, Integer totalTestCount) {
        if (totalTestCount == null || totalTestCount == 0) {
            return 0.0;
        }
        return (double) (passedTestCount != null ? passedTestCount : 0) / totalTestCount * 100;
    }

    /**
     * AI 피드백 준비 상태 확인
     */
    public boolean isReadyForAiFeedback(AlgoSubmissionDto submission) {
        return isJudgingCompleted(submission) &&
               submission.getSourceCode() != null &&
               !submission.getSourceCode().trim().isEmpty();
    }

    // ==================== AlgoTestcase 관련 ====================

    /**
     * 출력값 정규화 (공백, 개행 처리)
     */
    public String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ");
    }

    /**
     * 출력값 비교 (공백, 개행 무시)
     */
    public boolean isOutputMatched(String expectedOutput, String actualOutput) {
        if (expectedOutput == null && actualOutput == null) {
            return true;
        }
        if (expectedOutput == null || actualOutput == null) {
            return false;
        }
        return normalizeOutput(expectedOutput).equals(normalizeOutput(actualOutput));
    }

    /**
     * 문자열 요약 (긴 문자열의 경우 첫 부분만)
     */
    public String getSummary(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 실행 시간을 사람이 읽기 쉬운 형태로 변환
     */
    public String formatExecutionTime(Long executionTime) {
        if (executionTime == null) {
            return "-";
        }
        if (executionTime < 1000) {
            return executionTime + "ms";
        } else {
            return String.format("%.2fs", executionTime / 1000.0);
        }
    }

    /**
     * 메모리 사용량을 사람이 읽기 쉬운 형태로 변환
     */
    public String formatMemoryUsage(Long memoryUsage) {
        if (memoryUsage == null) {
            return "-";
        }
        if (memoryUsage < 1024) {
            return memoryUsage + "KB";
        } else {
            return String.format("%.2fMB", memoryUsage / 1024.0);
        }
    }
}
