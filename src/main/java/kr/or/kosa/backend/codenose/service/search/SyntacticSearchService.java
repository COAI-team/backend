package kr.or.kosa.backend.codenose.service.search;

import kr.or.kosa.backend.codenose.service.search.strategy.SyntacticAnalysisStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyntacticSearchService {

    private final List<SyntacticAnalysisStrategy> strategies;

    public Map<String, Object> extractFeatures(String code, String language) {
        System.out.println("[TRACE] SyntacticSearchService.extractFeatures called with code length: " + code.length()
                + ", language: " + language);

        SyntacticAnalysisStrategy strategy = strategies.stream()
                .filter(s -> s.supports(language))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Unsupported language for syntactic analysis: " + language));

        return strategy.extractFeatures(code);
    }

    // Legacy support or default
    public Map<String, Object> extractFeatures(String code) {
        return extractFeatures(code, "java");
    }

    public String getFeatureString(String code, String language) {
        System.out.println("[TRACE] SyntacticSearchService.getFeatureString called with code length: " + code.length()
                + ", language: " + language);
        try {
            Map<String, Object> features = extractFeatures(code, language);
            if (features.containsKey("error")) {
                return "";
            }
            return features.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(" "));
        } catch (Exception e) {
            log.warn("Feature extraction failed for language {}: {}", language, e.getMessage());
            return "";
        }
    }

    // Legacy support or default
    public String getFeatureString(String code) {
        return getFeatureString(code, "java");
    }
}
