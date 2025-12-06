package kr.or.kosa.backend.codenose.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final SyntacticSearchService syntacticSearchService;

    private static final int RRF_K = 60;

    public List<Document> search(String query, String codeSnippet, int topK) {
        System.out.println("[TRACE] HybridSearchService.search called with query: " + query);
        List<Document> semanticResults = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK * 2).build());

        List<Document> syntacticResults = new ArrayList<>();
        if (codeSnippet != null && !codeSnippet.isEmpty()) {
            String featureString = syntacticSearchService.getFeatureString(codeSnippet);
            syntacticResults = vectorStore.similaritySearch(
                    SearchRequest.builder().query(featureString).topK(topK * 2).build());
        }

        return performRRF(semanticResults, syntacticResults, topK);
    }

    private List<Document> performRRF(List<Document> semanticResults, List<Document> syntacticResults, int topK) {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (int i = 0; i < semanticResults.size(); i++) {
            Document doc = semanticResults.get(i);
            String id = doc.getId();
            docMap.putIfAbsent(id, doc);
            double score = 1.0 / (RRF_K + (i + 1));
            scoreMap.merge(id, score, Double::sum);
        }

        for (int i = 0; i < syntacticResults.size(); i++) {
            Document doc = syntacticResults.get(i);
            String id = doc.getId();
            docMap.putIfAbsent(id, doc);
            double score = 1.0 / (RRF_K + (i + 1));
            scoreMap.merge(id, score, Double::sum);
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .collect(Collectors.toList());
    }
}
