package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.external.ProblemDocumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 알고리즘 문제 Vector DB 저장/검색 서비스
 * RAG 기반 Few-shot 학습을 위한 문제 데이터 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemVectorStoreService {

    private final VectorStore vectorStore;

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:coai_documents}")
    private String collectionName;

    /**
     * 알고리즘 문제를 Vector DB에 저장
     *
     * @param problemDoc 저장할 문제 문서
     * @return 저장된 문서 ID
     */
    public String storeProblem(ProblemDocumentDto problemDoc) {
        String documentId = generateDocumentId(problemDoc);

        Document document = new Document(
                documentId,
                problemDoc.toEmbeddingContent(),
                problemDoc.toMetadata()
        );

        vectorStore.add(List.of(document));

        log.info("✅ Vector DB 저장 완료: [{}] {} ({})",
                problemDoc.getSource(),
                problemDoc.getTitle(),
                problemDoc.getDifficulty());

        return documentId;
    }

    /**
     * 여러 문제를 일괄 저장
     *
     * @param problems 저장할 문제 목록
     * @return 저장된 문서 수
     */
    public int storeProblems(List<ProblemDocumentDto> problems) {
        List<Document> documents = problems.stream()
                .map(p -> new Document(
                        generateDocumentId(p),
                        p.toEmbeddingContent(),
                        p.toMetadata()
                ))
                .toList();

        vectorStore.add(documents);

        log.info("✅ Vector DB 일괄 저장 완료: {}개 문제", documents.size());
        return documents.size();
    }

    /**
     * 유사 문제 검색 (토픽/난이도 기반)
     *
     * @param query 검색 쿼리 (주제, 키워드 등)
     * @param topK  반환할 최대 결과 수
     * @return 유사 문제 문서 목록
     */
    public List<Document> searchSimilarProblems(String query, int topK) {
        log.info("🔍 Vector DB 검색: query='{}', topK={}", query, topK);

        SearchRequest request = SearchRequest.builder()
                .similarityThreshold(0.7)
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ 검색 결과: {}개 문제", results.size());
        return results;
    }

    /**
     * 특정 소스의 문제만 검색
     *
     * @param query  검색 쿼리
     * @param source 소스 필터 (BOJ, LEETCODE)
     * @param topK   반환할 최대 결과 수
     * @return 필터링된 유사 문제 목록
     */
    public List<Document> searchBySource(String query, String source, int topK) {
        log.info("🔍 Vector DB 검색 (source={}): query='{}', topK={}", source, query, topK);

        String filterExpression = String.format("source == '%s'", source);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ 검색 결과: {}개 문제", results.size());
        return results;
    }

    /**
     * 난이도 범위로 문제 검색
     *
     * @param query      검색 쿼리
     * @param difficulty 난이도 필터
     * @param topK       반환할 최대 결과 수
     * @return 필터링된 유사 문제 목록
     */
    public List<Document> searchByDifficulty(String query, String difficulty, int topK) {
        log.info("🔍 Vector DB 검색 (difficulty={}): query='{}', topK={}", difficulty, query, topK);

        String filterExpression = String.format("difficulty == '%s'", difficulty);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ 검색 결과: {}개 문제", results.size());
        return results;
    }

    /**
     * Few-shot 학습용 유사 문제 검색
     * 태그와 난이도를 기반으로 가장 관련성 높은 문제 반환
     *
     * @param topic      알고리즘 주제 (예: "그래프 탐색", "동적 프로그래밍")
     * @param difficulty 목표 난이도
     * @param count      반환할 예시 문제 수
     * @return Few-shot 학습에 사용할 문제 목록
     */
    public List<Document> getFewShotExamples(String topic, String difficulty, int count) {
        String query = String.format("%s algorithm problem %s level", topic, difficulty);

        log.info("🎯 Few-shot 예시 검색: topic='{}', difficulty='{}', count={}",
                topic, difficulty, count);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(count)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ Few-shot 예시: {}개 문제 반환", results.size());
        return results;
    }

    /**
     * 문서 고유 ID 생성 (UUID 형식)
     * source + externalId 조합을 기반으로 결정적 UUID 생성
     * - 동일한 문제는 항상 같은 UUID를 가짐 (멱등성 보장)
     * - Qdrant의 UUID 요구사항 충족
     */
    private String generateDocumentId(ProblemDocumentDto problem) {
        String uniqueKey = String.format("%s_%s", problem.getSource(), problem.getExternalId());
        return UUID.nameUUIDFromBytes(uniqueKey.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Vector DB에서 문서 삭제 (ID 목록 기반)
     *
     * @param documentIds 삭제할 문서 ID 목록
     * @return 삭제 요청된 문서 수
     */
    public int deleteDocuments(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            log.warn("삭제할 문서 ID가 없습니다.");
            return 0;
        }

        vectorStore.delete(documentIds);
        log.info("✅ Vector DB에서 {}개 문서 삭제 완료", documentIds.size());
        return documentIds.size();
    }

    /**
     * 영어 문제 검색 및 ID 목록 반환
     * Qdrant REST API를 직접 호출하여 모든 문서를 스캔
     *
     * @param limit 검색할 최대 문서 수
     * @return 영어 문제 문서 목록 (ID, 제목 포함)
     */
    public List<Document> findEnglishProblems(int limit) {
        log.info("🔍 영어 문제 검색 중 (Qdrant 직접 스캔, limit={})", limit);

        List<Document> englishDocs = new ArrayList<>();
        String nextPageOffset = null;
        int batchSize = 50;
        int totalScanned = 0;

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://" + qdrantHost + ":6333")
                    .build();

            while (totalScanned < limit) {
                // Qdrant scroll API 호출
                Map<String, Object> requestBody = new java.util.HashMap<>();
                requestBody.put("limit", Math.min(batchSize, limit - totalScanned));
                requestBody.put("with_payload", true);
                requestBody.put("with_vector", false);
                if (nextPageOffset != null) {
                    requestBody.put("offset", nextPageOffset);
                }

                Map<String, Object> response = webClient.post()
                        .uri("/collections/" + collectionName + "/points/scroll")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .block();

                if (response == null || !response.containsKey("result")) {
                    log.warn("Qdrant 응답이 비어있습니다.");
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");

                if (points == null || points.isEmpty()) {
                    log.info("더 이상 문서가 없습니다.");
                    break;
                }

                for (Map<String, Object> point : points) {
                    totalScanned++;
                    String id = point.get("id").toString();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) point.get("payload");

                    if (payload == null) continue;

                    String docContent = (String) payload.get("doc_content");
                    String title = (String) payload.getOrDefault("title", "Unknown");

                    // Description 부분에 한국어가 없으면 영어 문제
                    if (docContent != null && !hasKoreanDescription(docContent)) {
                        Document doc = new Document(id, docContent, payload);
                        englishDocs.add(doc);
                        log.debug("🔤 영어 문제 발견: {}", title);
                    }
                }

                // 다음 페이지 오프셋
                nextPageOffset = result.get("next_page_offset") != null
                        ? result.get("next_page_offset").toString()
                        : null;

                if (nextPageOffset == null) {
                    log.info("마지막 페이지 도달");
                    break;
                }
            }

            log.info("✅ 총 {}개 문서 스캔, 영어 문제 {}개 발견", totalScanned, englishDocs.size());

        } catch (Exception e) {
            log.error("Qdrant 스캔 중 오류 발생", e);
        }

        return englishDocs;
    }

    /**
     * 문서의 Description 부분에 한국어가 있는지 확인
     * Tags에는 한국어가 있어도 Description 본문이 영어면 영어 문제로 판단
     * "입력:", "출력:" 같은 라벨은 제외하고 실제 설명만 체크
     */
    private boolean hasKoreanDescription(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Description 부분 추출
        int descStart = text.indexOf("Description:");
        if (descStart == -1) {
            // Description 마커가 없으면 전체 텍스트로 판단
            return containsKorean(text);
        }

        // Description 본문만 추출 (입력: 또는 Sample Input: 이전까지)
        String afterDesc = text.substring(descStart + "Description:".length());

        // 입력 섹션 시작점 찾기 (여러 가능한 마커)
        int inputStart = Integer.MAX_VALUE;
        String[] inputMarkers = {"입력:", "입력 :", "Input:", "Sample Input:"};
        for (String marker : inputMarkers) {
            int idx = afterDesc.indexOf(marker);
            if (idx != -1 && idx < inputStart) {
                inputStart = idx;
            }
        }

        // Description 본문만 추출
        String descriptionBody;
        if (inputStart < Integer.MAX_VALUE) {
            descriptionBody = afterDesc.substring(0, inputStart);
        } else {
            descriptionBody = afterDesc;
        }

        // Description 본문에서 한국어 체크 (라벨 제외, 실제 내용만)
        return containsKoreanContent(descriptionBody);
    }

    /**
     * 텍스트에 한국어 내용이 있는지 확인
     * 단순 라벨이 아닌 실제 한국어 문장이 있는지 체크
     */
    private boolean containsKoreanContent(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // 한국어 문자 개수 세기
        long koreanCharCount = text.chars()
                .filter(c -> (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x3131 && c <= 0x3163))
                .count();

        // 전체 알파벳/한글 문자 중 한국어 비율이 10% 이상이면 한국어 문서
        long totalLetters = text.chars()
                .filter(c -> Character.isLetter(c))
                .count();

        if (totalLetters == 0) {
            return false;
        }

        double koreanRatio = (double) koreanCharCount / totalLetters;
        return koreanRatio > 0.1;  // 10% 이상이면 한국어
    }

    /**
     * 영어 문제 일괄 삭제
     * 한국어가 포함되지 않은 문제를 검색하여 삭제
     *
     * @param searchLimit 검색할 최대 문서 수
     * @return 삭제된 문서 수
     */
    public int deleteEnglishProblems(int searchLimit) {
        log.info("🗑️ 영어 문제 삭제 시작 (searchLimit={})", searchLimit);

        List<Document> englishDocs = findEnglishProblems(searchLimit);

        if (englishDocs.isEmpty()) {
            log.info("삭제할 영어 문제가 없습니다.");
            return 0;
        }

        List<String> idsToDelete = englishDocs.stream()
                .map(Document::getId)
                .toList();

        // 삭제 전 로깅
        englishDocs.forEach(doc -> {
            String title = doc.getMetadata().getOrDefault("title", "Unknown").toString();
            log.info("🗑️ 삭제 예정: {} (ID: {})", title, doc.getId());
        });

        vectorStore.delete(idsToDelete);
        log.info("✅ {}개 영어 문제 삭제 완료", idsToDelete.size());
        return idsToDelete.size();
    }

    /**
     * 문자열에 한국어가 포함되어 있는지 확인
     */
    private boolean containsKorean(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 한글 유니코드 범위: 가-힣 (완성형), ㄱ-ㅎ (자음), ㅏ-ㅣ (모음)
        return text.chars().anyMatch(c ->
                (c >= 0xAC00 && c <= 0xD7A3) ||  // 완성형 한글
                (c >= 0x3131 && c <= 0x3163)    // 자음/모음
        );
    }

    /**
     * 모든 문제 삭제 (초기화용 - 주의해서 사용)
     * Vector DB의 모든 문서를 검색하여 삭제
     *
     * @param confirmDelete true를 전달해야 실제 삭제 수행
     * @return 삭제된 문서 수
     */
    public int deleteAllProblems(boolean confirmDelete) {
        if (!confirmDelete) {
            log.warn("⚠️ deleteAllProblems 호출됨 - confirmDelete=false로 삭제 취소");
            return 0;
        }

        log.warn("⚠️ Vector DB 전체 삭제 시작");

        // 많은 수의 문서를 검색하여 삭제
        SearchRequest request = SearchRequest.builder()
                .query("algorithm problem")
                .topK(1000)
                .build();

        List<Document> allDocs = vectorStore.similaritySearch(request);

        if (allDocs.isEmpty()) {
            log.info("삭제할 문서가 없습니다.");
            return 0;
        }

        List<String> idsToDelete = allDocs.stream()
                .map(Document::getId)
                .toList();

        vectorStore.delete(idsToDelete);
        log.warn("⚠️ {}개 문서 삭제 완료", idsToDelete.size());
        return idsToDelete.size();
    }
}
