package kr.or.kosa.backend.battle.service.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import kr.or.kosa.backend.algorithm.dto.AICodeEvaluationResult;
import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.service.CodeEvaluationService;
import kr.or.kosa.backend.algorithm.service.LanguageService;
import kr.or.kosa.backend.battle.port.BattleJudgePort;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeCommand;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StubBattleJudgeAdapter implements BattleJudgePort {

    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);
    private static final BigDecimal LANGUAGE_MISMATCH_PENALTY = new BigDecimal("0.20");
    private static final long JUDGE_TIMEOUT_SECONDS = 25L;

    private final CodeEvaluationService codeEvaluationService;
    private final AlgorithmProblemMapper algorithmProblemMapper;
    private final LanguageService languageService;

    @Override
    public BattleJudgeResult judge(BattleJudgeCommand command) {
        String source = command.getSource();
        String trimmedSource = source != null ? source.trim() : "";
        if (trimmedSource.isEmpty()) {
            return BattleJudgeResult.builder()
                    .accepted(true)
                    .score(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .message("\uC81C\uCD9C \uCF54\uB4DC\uAC00 \uB108\uBB34 \uC9E7\uC544 0\uC810 \uCC98\uB9AC\uD588\uC2B5\uB2C8\uB2E4.")
                    .build();
        }
        AlgoProblemDto problem = algorithmProblemMapper.selectProblemById(command.getProblemId());
        String problemDescription = extractProblemDescription(problem);
        String languageName = resolveLanguageName(command.getLanguageId());

        int sourceLen = source != null ? source.length() : 0;
        int promptLen = problemDescription != null ? problemDescription.length() : 0;

        log.info("[battle] matchId={} userId={} action=judge-start problemId={} languageId={} languageName={} sourceLen={} promptLen={}",
                command.getMatchId(), command.getUserId(), command.getProblemId(), command.getLanguageId(), languageName, sourceLen, promptLen);

        try {
            AICodeEvaluationResult evaluation = codeEvaluationService
                    .evaluateCode(command.getSource(), problemDescription, languageName, "UNKNOWN")
                    .get(JUDGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (evaluation == null) {
                log.warn("[battle] matchId={} userId={} action=judge-null-result",
                        command.getMatchId(), command.getUserId());
                return BattleJudgeResult.rejected("\uCC44\uC810 \uACB0\uACFC\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.");
            }

            BigDecimal score = applyStrictScore(normalizeScore(evaluation.getAiScore()));
            String message = StringUtils.hasText(evaluation.getFeedback())
                    ? evaluation.getFeedback()
                    : "\uCC44\uC810 \uC644\uB8CC";

            String expectedKey = normalizeLanguageKey(languageName);
            String actualKey = inferLanguageKey(trimmedSource);
            if (expectedKey != null && actualKey != null && !expectedKey.equals(actualKey)) {
                score = applyLanguageMismatchPenalty(score);
                message = appendLanguageMismatchMessage(message, expectedKey, actualKey);
                log.info("[battle] matchId={} userId={} action=language-mismatch expected={} actual={} score={}",
                        command.getMatchId(), command.getUserId(), expectedKey, actualKey, score);
            }

            log.info("[battle] matchId={} userId={} action=judge-ok score={} hasMessage={}",
                    command.getMatchId(), command.getUserId(), score, StringUtils.hasText(message));

            return BattleJudgeResult.builder()
                    .accepted(true)
                    .score(score)
                    .message(message)
                    .build();

        } catch (TimeoutException e) {
            log.error("[battle] matchId={} userId={} action=judge-timeout timeoutSec={}",
                    command.getMatchId(), command.getUserId(), JUDGE_TIMEOUT_SECONDS, e);
            return BattleJudgeResult.rejected("\uCC44\uC810 \uC2DC\uAC04 \uCD08\uACFC\uB418\uC5C8\uC2B5\uB2C8\uB2E4. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.");
        } catch (Exception e) {
            log.error("[battle] matchId={} userId={} action=judge-failed error={}",
                    command.getMatchId(), command.getUserId(), e.getMessage(), e);
            return BattleJudgeResult.rejected("\uCC44\uC810 \uC911 \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC2B5\uB2C8\uB2E4.");
        }
    }

    private String extractProblemDescription(AlgoProblemDto problem) {
        if (problem == null) return "\uBB38\uC81C \uC124\uBA85\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.";
        if (StringUtils.hasText(problem.getAlgoProblemDescription())) {
            return problem.getAlgoProblemDescription();
        }
        if (StringUtils.hasText(problem.getAlgoProblemTitle())) {
            return problem.getAlgoProblemTitle();
        }
        return "\uBB38\uC81C \uC124\uBA85\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.";
    }

    private String resolveLanguageName(Long languageId) {
        if (languageId == null) return "Unknown";
        LanguageDto language = languageService.getById(languageId.intValue());
        if (language != null && StringUtils.hasText(language.getLanguageName())) {
            return language.getLanguageName();
        }
        return "Unknown";
    }

    private BigDecimal normalizeScore(Double rawScore) {
        BigDecimal score = rawScore == null ? BigDecimal.ZERO : BigDecimal.valueOf(rawScore);
        if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
        if (score.compareTo(MAX_SCORE) > 0) score = MAX_SCORE;
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyStrictScore(BigDecimal score) {
        if (score == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ten = new BigDecimal("10.00");
        BigDecimal fifty = new BigDecimal("50.00");
        BigDecimal ninety = new BigDecimal("90.00");
        BigDecimal adjusted;
        if (score.compareTo(ten) <= 0) {
            adjusted = score;
        } else if (score.compareTo(fifty) <= 0) {
            adjusted = ten.add(score.subtract(ten).multiply(new BigDecimal("0.5")));
        } else if (score.compareTo(ninety) <= 0) {
            adjusted = new BigDecimal("30.00").add(score.subtract(fifty).multiply(new BigDecimal("1.5")));
        } else {
            adjusted = score;
        }
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) adjusted = BigDecimal.ZERO;
        if (adjusted.compareTo(MAX_SCORE) > 0) adjusted = MAX_SCORE;
        return adjusted.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyLanguageMismatchPenalty(BigDecimal score) {
        if (score == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjusted = score.multiply(LANGUAGE_MISMATCH_PENALTY);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) adjusted = BigDecimal.ZERO;
        if (adjusted.compareTo(MAX_SCORE) > 0) adjusted = MAX_SCORE;
        return adjusted.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeLanguageKey(String languageName) {
        if (!StringUtils.hasText(languageName)) return null;
        String lower = languageName.trim().toLowerCase();
        if (lower.contains("c#") || lower.contains("csharp")) return "csharp";
        if (lower.contains("c++") || lower.contains("cpp")) return "cpp";
        if (lower.contains("javascript") || lower.equals("js")) return "javascript";
        if (lower.contains("typescript") || lower.equals("ts")) return "typescript";
        if (lower.contains("python") || lower.equals("py")) return "python";
        if (lower.contains("kotlin")) return "kotlin";
        if (lower.contains("golang") || lower.equals("go")) return "go";
        if (lower.contains("rust")) return "rust";
        if (lower.contains("sql")) return "sql";
        if (lower.equals("java") || lower.contains("java")) return "java";
        return null;
    }

    private String inferLanguageKey(String source) {
        if (!StringUtils.hasText(source)) return null;
        String lower = source.toLowerCase();
        if (lower.contains("#include") || lower.contains("std::") || lower.contains("using namespace std")) return "cpp";
        if (lower.contains("using system") || lower.contains("console.writeline")) return "csharp";
        if (lower.contains("public static void main") || lower.contains("system.out")) return "java";
        if (lower.contains("fun main")
                || (lower.contains("val ") && lower.contains("println("))
                || (lower.contains("var ") && lower.contains("println("))) {
            return "kotlin";
        }
        if (lower.contains("def ") || lower.contains("print(") || lower.contains("import sys") || lower.contains("sys.stdin")) {
            return "python";
        }
        if (lower.contains("interface ") || lower.contains(": number") || lower.contains(": string") || lower.contains(": boolean")) {
            return "typescript";
        }
        if (lower.contains("console.log") || lower.contains("function ") || lower.contains("=>")) return "javascript";
        if (lower.contains("package main") || lower.contains("func main") || lower.contains("fmt.")) return "go";
        if (lower.contains("fn main") || lower.contains("println!") || lower.contains("use std")) return "rust";
        if (lower.contains("select ") && lower.contains(" from ")) return "sql";
        return null;
    }

    private String appendLanguageMismatchMessage(String message, String expectedKey, String actualKey) {
        String base = StringUtils.hasText(message) ? message.trim() : "\uCC44\uC810 \uC644\uB8CC";
        String expectedLabel = labelForLanguageKey(expectedKey);
        String actualLabel = labelForLanguageKey(actualKey);
        String penalty = String.format(
                "\uC120\uD0DD \uC5B8\uC5B4(%s)\uC640 \uB2E4\uB978 \uC5B8\uC5B4(%s)\uB85C \uC791\uC131\uB418\uC5B4 \uD070 \uAC10\uC810\uC774 \uC801\uC6A9\uB418\uC5C8\uC2B5\uB2C8\uB2E4.",
                expectedLabel,
                actualLabel
        );
        if (base.contains(penalty)) return base;
        return base + "\n" + penalty;
    }

    private String labelForLanguageKey(String key) {
        if (key == null) return "\uC54C \uC218 \uC5C6\uC74C";
        return switch (key) {
            case "cpp" -> "C++";
            case "csharp" -> "C#";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "python" -> "Python";
            case "kotlin" -> "Kotlin";
            case "go" -> "Go";
            case "rust" -> "Rust";
            case "sql" -> "SQL";
            case "java" -> "Java";
            default -> key;
        };
    }
}
