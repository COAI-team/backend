package kr.or.kosa.backend.codenose.service.search;

import kr.or.kosa.backend.codenose.service.search.strategy.SyntacticAnalysisStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 구문적 검색 서비스 (SyntacticSearchService)
 * 
 * 역할:
 * 코드의 구조적 특징(Identifier, Method Name 등)을 추출하여 검색에 활용할 수 있는 문자열로 변환합니다.
 * 언어별(Java, Python, JS 등) 전략 패턴을 사용하여 확장성을 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyntacticSearchService {

    // 등록된 모든 구문 분석 전략(Strategy) 리스트
    private final List<SyntacticAnalysisStrategy> strategies;

    /**
     * 코드에서 구문적 특징 추출
     * 
     * 입력된 언어에 맞는 적절한 전략(Strategy)을 찾아 특징(Features)을 추출합니다.
     * 
     * @param code     분석할 코드
     * @param language 프로그래밍 언어 (java, python, javascript 등)
     * @return 특징 맵 (Key: 특징 유형, Value: 특징 값)
     */
    public Map<String, Object> extractFeatures(String code, String language) {
        SyntacticAnalysisStrategy strategy = strategies.stream()
                .filter(s -> s.supports(language))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("지원하지 않는 언어입니다: " + language));

        return strategy.extractFeatures(code);
    }

    /**
     * 특징을 검색용 문자열로 변환
     * 
     * 추출된 특징 맵을 "Key:Value" 형태의 단일 문자열로 변환하여 벡터 DB 쿼리에 사용합니다.
     * 
     * @param code     분석할 코드
     * @param language 프로그래밍 언어
     * @return 특징 문자열 (예: "method:calculate class:Calculator")
     */
    public String getFeatureString(String code, String language) {
        try {
            Map<String, Object> features = extractFeatures(code, language);
            if (features.containsKey("error")) {
                return "";
            }
            return features.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(" "));
        } catch (Exception e) {
            log.warn("구문 특징 추출 실패 - 언어: {}, 오류: {}", language, e.getMessage());
            return "";
        }
    }
}
