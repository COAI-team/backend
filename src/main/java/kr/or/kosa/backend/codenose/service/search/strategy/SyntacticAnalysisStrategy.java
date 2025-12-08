package kr.or.kosa.backend.codenose.service.search.strategy;

import java.util.Map;

public interface SyntacticAnalysisStrategy {
    /**
     * Extracts syntactic features from the given code.
     *
     * @param code The source code to analyze
     * @return A map of feature names to their values (e.g., "max_loop_depth" -> 3)
     */
    Map<String, Object> extractFeatures(String code);

    /**
     * Checks if this strategy supports the given language.
     *
     * @param language The language identifier (e.g., "java", "python")
     * @return true if supported
     */
    boolean supports(String language);
}
