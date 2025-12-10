package kr.or.kosa.backend.codenose.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 하이브리드 검색 서비스 (HybridSearchService)
 * 
 * 역할:
 * 시맨틱 검색(의미 기반)과 구문적 검색(코드 구조 기반)을 결합하여 검색 정확도를 높입니다.
 * RRF(Reciprocal Rank Fusion) 알고리즘을 사용하여 두 가지 검색 결과의 순위를 재조정합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final SyntacticSearchService syntacticSearchService;

    // RRF 알고리즘의 상수 K (순위 보정값)
    private static final int RRF_K = 60;

    /**
     * 하이브리드 검색 수행
     * 
     * 1. 시맨틱 검색 (Semantic Search): 질문(query)의 의미를 벡터로 변환하여 유사한 문서를 찾습니다.
     * 2. 구문적 검색 (Syntactic Search): 코드 스니펫에서 특징(메서드명, 변수명 등)을 추출하여 유사한 구조의 코드를
     * 찾습니다.
     * 3. RRF (Reciprocal Rank Fusion): 두 검색 결과의 순위를 통합하여 최종 결과를 도출합니다.
     * 
     * @param query       사용자 질문
     * @param codeSnippet 분석 대상 코드 (구문적 특징 추출용)
     * @param topK        최종 반환할 문서 개수
     * @param language    프로그래밍 언어
     * @return 통합된 검색 결과 리스트
     */
    public List<Document> search(String query, String codeSnippet, int topK, String language) {
        log.debug("하이브리드 검색 시작 - query: {}, language: {}", query, language);

        // 1. 시맨틱 검색 수행 (벡터 유사도)
        List<Document> semanticResults = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK * 2).build());

        // 2. 구문적 검색 수행 (코드 특징 기반)
        List<Document> syntacticResults = new ArrayList<>();
        if (codeSnippet != null && !codeSnippet.isEmpty()) {
            String featureString = syntacticSearchService.getFeatureString(codeSnippet, language);
            if (!featureString.isEmpty()) {
                syntacticResults = vectorStore.similaritySearch(
                        SearchRequest.builder().query(featureString).topK(topK * 2).build());
            }
        }

        // 3. RRF 알고리즘으로 결과 통합
        return performRRF(semanticResults, syntacticResults, topK);
    }

    public List<Document> search(String query, String codeSnippet, int topK) {
        return search(query, codeSnippet, topK, "java");
    }

    /**
     * RRF (Reciprocal Rank Fusion) 알고리즘 수행
     * 
     * 여러 검색 결과 리스트에서 각 문서의 순위(Rank)를 기반으로 점수를 매기고 합산합니다.
     * 점수 공식: Score = 1 / (K + Rank)
     * 
     * @param semanticResults  시맨틱 검색 결과 리스트
     * @param syntacticResults 구문적 검색 결과 리스트
     * @param topK             최종 반환할 개수
     * @return 재정렬된 문서 리스트
     */
    private List<Document> performRRF(List<Document> semanticResults, List<Document> syntacticResults, int topK) {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        // 시맨틱 결과 점수 산정
        for (int i = 0; i < semanticResults.size(); i++) {
            Document doc = semanticResults.get(i);
            String id = doc.getId();
            docMap.putIfAbsent(id, doc);
            double score = 1.0 / (RRF_K + (i + 1));
            scoreMap.merge(id, score, Double::sum);
        }

        // 구문적 결과 점수 산정
        for (int i = 0; i < syntacticResults.size(); i++) {
            Document doc = syntacticResults.get(i);
            String id = doc.getId();
            docMap.putIfAbsent(id, doc);
            double score = 1.0 / (RRF_K + (i + 1));
            scoreMap.merge(id, score, Double::sum);
        }

        // 점수 기준 내림차순 정렬 후 상위 topK 반환
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .collect(Collectors.toList());
    }
}
