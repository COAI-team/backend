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

    // ===== AI 생성 문제 유사도 검사 및 저장 =====

    /**
     * AI 생성 문제의 유사도 검사
     * 제목과 설명을 결합하여 Vector DB에서 유사한 문제 검색
     *
     * @param title       문제 제목
     * @param description 문제 설명
     * @param threshold   유사도 임계값 (0.0 ~ 1.0)
     * @return 유사도 검사 결과 (유사 문제 목록, 최대 유사도 등)
     */
    public SimilarityCheckResult checkSimilarity(String title, String description, double threshold) {
        log.info("🔍 AI 생성 문제 유사도 검사 시작 - 제목: {}, 임계값: {}", title, threshold);

        SimilarityCheckResult result = new SimilarityCheckResult();
        result.setThreshold(threshold);

        try {
            // 제목 + 설명을 결합하여 검색 쿼리 생성
            String query = String.format("%s %s", title, description);

            // 유사한 문제 검색 (상위 5개)
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(5)
                    .similarityThreshold(0.5)  // 낮은 임계값으로 일단 검색
                    .build();

            List<Document> similarDocs = vectorStore.similaritySearch(request);

            if (similarDocs.isEmpty()) {
                log.info("✅ 유사한 문제 없음 - 유사도 검사 통과");
                result.setPassed(true);
                result.setMaxSimilarity(0.0);
                return result;
            }

            // 유사도 계산 (Spring AI는 score를 metadata에 포함하지 않으므로 직접 계산)
            double maxSimilarity = 0.0;
            Document mostSimilar = null;

            for (Document doc : similarDocs) {
                // 텍스트 유사도 계산 (Jaccard + 공통 키워드 기반)
                String docContent = doc.getText();
                double similarity = calculateContentSimilarity(query, docContent);

                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    mostSimilar = doc;
                }
            }

            result.setMaxSimilarity(maxSimilarity);
            result.setSimilarDocuments(similarDocs);

            if (mostSimilar != null) {
                result.setMostSimilarTitle((String) mostSimilar.getMetadata().get("title"));
                result.setMostSimilarId((String) mostSimilar.getMetadata().get("externalId"));
            }

            // 임계값 검사
            if (maxSimilarity >= threshold) {
                result.setPassed(false);
                log.warn("⚠️ 유사도 검사 실패 - 최대 유사도: {:.2f} >= 임계값: {:.2f}, 유사 문제: {}",
                        maxSimilarity, threshold, result.getMostSimilarTitle());
            } else {
                result.setPassed(true);
                log.info("✅ 유사도 검사 통과 - 최대 유사도: {:.2f} < 임계값: {:.2f}",
                        maxSimilarity, threshold);
            }

        } catch (Exception e) {
            log.error("유사도 검사 중 오류 발생", e);
            result.setPassed(true);  // 오류 시 일단 통과 (검사 실패로 인한 블로킹 방지)
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * 콘텐츠 유사도 계산 (Jaccard + 키워드 기반)
     */
    private double calculateContentSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        // 토큰화
        java.util.Set<String> tokens1 = tokenize(text1);
        java.util.Set<String> tokens2 = tokenize(text2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        // Jaccard 유사도
        java.util.Set<String> intersection = new java.util.HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        java.util.Set<String> union = new java.util.HashSet<>(tokens1);
        union.addAll(tokens2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * 텍스트 토큰화
     */
    private java.util.Set<String> tokenize(String text) {
        if (text == null) {
            return java.util.Collections.emptySet();
        }
        String[] tokens = text.toLowerCase()
                .replaceAll("[^a-z0-9가-힣\\s]", " ")
                .trim()
                .split("\\s+");
        return new java.util.HashSet<>(java.util.Arrays.asList(tokens));
    }

    /**
     * AI 생성 문제를 Vector DB에 저장
     *
     * @param problemId   MySQL DB의 문제 ID
     * @param title       문제 제목
     * @param description 문제 설명
     * @param difficulty  난이도
     * @param tags        태그 목록
     * @return 저장된 문서 ID
     */
    public String storeGeneratedProblem(Long problemId, String title, String description,
                                        String difficulty, List<String> tags) {
        log.info("📝 AI 생성 문제 Vector DB 저장 - ID: {}, 제목: {}", problemId, title);

        // 문서 ID 생성 (AI_GENERATED + problemId)
        String documentId = UUID.nameUUIDFromBytes(
                String.format("AI_GENERATED_%d", problemId).getBytes(StandardCharsets.UTF_8)
        ).toString();

        // 임베딩용 콘텐츠 생성
        String content = String.format(
                "제목: %s\n난이도: %s\n태그: %s\n설명: %s",
                title,
                difficulty,
                tags != null ? String.join(", ", tags) : "",
                description
        );

        // 메타데이터 구성
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("source", "AI_GENERATED");
        metadata.put("externalId", String.valueOf(problemId));
        metadata.put("title", title);
        metadata.put("difficulty", difficulty);
        metadata.put("tags", tags != null ? String.join(",", tags) : "");

        Document document = new Document(documentId, content, metadata);
        vectorStore.add(List.of(document));

        log.info("✅ AI 생성 문제 Vector DB 저장 완료 - docId: {}", documentId);
        return documentId;
    }

    /**
     * 유사도 검사 결과 클래스
     */
    public static class SimilarityCheckResult {
        private boolean passed;
        private double maxSimilarity;
        private double threshold;
        private String mostSimilarTitle;
        private String mostSimilarId;
        private List<Document> similarDocuments;
        private String error;

        // Getters and Setters
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }

        public double getMaxSimilarity() { return maxSimilarity; }
        public void setMaxSimilarity(double maxSimilarity) { this.maxSimilarity = maxSimilarity; }

        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }

        public String getMostSimilarTitle() { return mostSimilarTitle; }
        public void setMostSimilarTitle(String mostSimilarTitle) { this.mostSimilarTitle = mostSimilarTitle; }

        public String getMostSimilarId() { return mostSimilarId; }
        public void setMostSimilarId(String mostSimilarId) { this.mostSimilarId = mostSimilarId; }

        public List<Document> getSimilarDocuments() { return similarDocuments; }
        public void setSimilarDocuments(List<Document> similarDocuments) { this.similarDocuments = similarDocuments; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getSummary() {
            if (error != null) {
                return String.format("오류: %s", error);
            }
            return String.format("통과=%s, 최대유사도=%.2f, 임계값=%.2f, 유사문제=%s",
                    passed, maxSimilarity, threshold, mostSimilarTitle);
        }
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
     * tags 필드에서 태그 목록 추출
     * tags는 List<String>, String(JSON 배열), 또는 String(쉼표 구분) 형태일 수 있음
     *
     * @param tagsObj payload에서 가져온 tags 객체
     * @return 태그 목록
     */
    @SuppressWarnings("unchecked")
    private List<String> extractTags(Object tagsObj) {
        if (tagsObj == null) {
            return null;
        }

        // 이미 List인 경우
        if (tagsObj instanceof List) {
            return (List<String>) tagsObj;
        }

        // String인 경우 (JSON 배열 또는 쉼표 구분)
        if (tagsObj instanceof String tagsStr) {
            if (tagsStr.isBlank()) {
                return null;
            }

            // JSON 배열 형태인 경우: ["tag1", "tag2"]
            if (tagsStr.startsWith("[")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    return mapper.readValue(tagsStr, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception e) {
                    log.warn("태그 JSON 파싱 실패: {}", tagsStr);
                    return null;
                }
            }

            // 쉼표로 구분된 문자열인 경우: "tag1, tag2"
            return java.util.Arrays.stream(tagsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return null;
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
     * Vector DB 컬렉션 통계 조회
     * 난이도별, 토픽별 문서 수 집계
     *
     * @return 컬렉션 통계 정보
     */
    public VectorDbStats getCollectionStats() {
        log.info("📊 Vector DB 통계 조회 시작");

        VectorDbStats stats = new VectorDbStats();
        String nextPageOffset = null;
        int batchSize = 100;
        int totalScanned = 0;
        int maxDocuments = 2000; // 최대 스캔 문서 수

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://" + qdrantHost + ":6333")
                    .build();

            while (totalScanned < maxDocuments) {
                // Qdrant scroll API 호출
                Map<String, Object> requestBody = new java.util.HashMap<>();
                requestBody.put("limit", batchSize);
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
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) point.get("payload");

                    if (payload == null) continue;

                    // 난이도 집계
                    String difficulty = (String) payload.get("difficulty");
                    if (difficulty != null && !difficulty.isEmpty()) {
                        stats.incrementDifficulty(difficulty);
                    }

                    // 토픽 집계 (tags 필드에서 추출)
                    // tags는 List<String> 또는 String(JSON) 형태일 수 있음
                    List<String> tags = extractTags(payload.get("tags"));
                    if (tags != null && !tags.isEmpty()) {
                        for (String tag : tags) {
                            stats.incrementTopic(tag);
                        }
                    }

                    // 난이도×토픽 조합 집계
                    if (difficulty != null && tags != null && !tags.isEmpty()) {
                        for (String tag : tags) {
                            stats.incrementCombination(difficulty, tag);
                        }
                    }

                    // 소스 집계
                    String source = (String) payload.get("source");
                    if (source != null) {
                        stats.incrementSource(source);
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

            stats.setTotalDocuments(totalScanned);
            log.info("✅ Vector DB 통계 조회 완료: 총 {}개 문서", totalScanned);

        } catch (Exception e) {
            log.error("Vector DB 통계 조회 중 오류 발생", e);
            stats.setError(e.getMessage());
        }

        return stats;
    }

    /**
     * Vector DB 통계 데이터 클래스
     */
    public static class VectorDbStats {
        private int totalDocuments;
        private final Map<String, Integer> byDifficulty = new java.util.HashMap<>();
        private final Map<String, Integer> byTopic = new java.util.HashMap<>();
        private final Map<String, Integer> bySource = new java.util.HashMap<>();
        private final Map<String, Map<String, Integer>> byCombination = new java.util.HashMap<>();
        private String error;

        public void incrementDifficulty(String difficulty) {
            byDifficulty.merge(difficulty, 1, Integer::sum);
        }

        public void incrementTopic(String topic) {
            byTopic.merge(topic, 1, Integer::sum);
        }

        public void incrementSource(String source) {
            bySource.merge(source, 1, Integer::sum);
        }

        public void incrementCombination(String difficulty, String topic) {
            byCombination.computeIfAbsent(difficulty, k -> new java.util.HashMap<>())
                    .merge(topic, 1, Integer::sum);
        }

        public void setTotalDocuments(int total) { this.totalDocuments = total; }
        public void setError(String error) { this.error = error; }

        public int getTotalDocuments() { return totalDocuments; }
        public Map<String, Integer> getByDifficulty() { return byDifficulty; }
        public Map<String, Integer> getByTopic() { return byTopic; }
        public Map<String, Integer> getBySource() { return bySource; }
        public Map<String, Map<String, Integer>> getByCombination() { return byCombination; }
        public String getError() { return error; }

        // 영어 토픽 → 한국어 태그 매핑 (Vector DB에 저장된 한국어 태그와 매칭)
        private static final Map<String, List<String>> TOPIC_KOREAN_MAP = Map.ofEntries(
            Map.entry("implementation", List.of("구현")),
            Map.entry("greedy", List.of("그리디 알고리즘")),
            Map.entry("sorting", List.of("정렬")),
            Map.entry("binary_search", List.of("이분 탐색", "매개 변수 탐색")),
            Map.entry("bruteforcing", List.of("브루트포스 알고리즘")),
            Map.entry("bfs", List.of("너비 우선 탐색")),
            Map.entry("dfs", List.of("깊이 우선 탐색")),
            Map.entry("dp", List.of("다이나믹 프로그래밍", "비트필드를 이용한 다이나믹 프로그래밍", "트리에서의 다이나믹 프로그래밍")),
            Map.entry("divide_and_conquer", List.of("분할 정복")),
            Map.entry("backtracking", List.of("백트래킹")),
            Map.entry("data_structures", List.of("자료 구조", "스택", "큐", "덱")),
            Map.entry("hashing", List.of("해싱", "해시를 사용한 집합과 맵")),
            Map.entry("priority_queue", List.of("우선순위 큐")),
            Map.entry("graphs", List.of("그래프 이론", "그래프 탐색")),
            Map.entry("shortest_path", List.of("최단 경로", "데이크스트라", "플로이드–워셜", "벨만–포드")),
            Map.entry("trees", List.of("트리", "세그먼트 트리")),
            Map.entry("disjoint_set", List.of("분리 집합")),
            Map.entry("string", List.of("문자열", "KMP", "라빈–카프")),
            Map.entry("math", List.of("수학", "정수론", "조합론")),
            Map.entry("bitmask", List.of("비트마스킹")),
            Map.entry("two_pointer", List.of("두 포인터")),
            Map.entry("sliding_window", List.of("슬라이딩 윈도우")),
            Map.entry("simulation", List.of("시뮬레이션"))
        );

        /**
         * 부족한 카테고리 목록 반환 (기대치 대비)
         * 한국어 태그와 영어 토픽 간 매핑을 사용하여 정확한 카운트 계산
         *
         * @param expectedPerCategory 카테고리당 기대 문서 수
         * @return 부족한 카테고리 목록
         */
        public List<Map<String, Object>> getMissingCategories(int expectedPerCategory) {
            List<Map<String, Object>> missing = new ArrayList<>();

            List<String> difficulties = List.of("BRONZE", "SILVER", "GOLD", "PLATINUM");
            List<String> topics = List.of(
                "implementation", "greedy", "sorting", "binary_search", "bruteforcing",
                "bfs", "dfs", "dp", "divide_and_conquer", "backtracking",
                "data_structures", "hashing", "priority_queue", "graphs", "shortest_path",
                "trees", "disjoint_set", "string", "math", "bitmask",
                "two_pointer", "sliding_window", "simulation"
            );

            for (String diff : difficulties) {
                Map<String, Integer> topicCounts = byCombination.getOrDefault(diff, new java.util.HashMap<>());
                for (String topic : topics) {
                    // 영어 토픽에 해당하는 한국어 태그들의 카운트 합산
                    int count = getKoreanTagCount(topicCounts, topic);
                    if (count < expectedPerCategory) {
                        Map<String, Object> entry = new java.util.HashMap<>();
                        entry.put("difficulty", diff);
                        entry.put("topic", topic);
                        entry.put("topicKorean", getKoreanTopicName(topic));
                        entry.put("count", count);
                        entry.put("expected", expectedPerCategory);
                        entry.put("missing", expectedPerCategory - count);
                        missing.add(entry);
                    }
                }
            }

            return missing;
        }

        /**
         * 영어 토픽에 해당하는 한국어 태그들의 카운트 합산
         */
        private int getKoreanTagCount(Map<String, Integer> topicCounts, String englishTopic) {
            List<String> koreanTags = TOPIC_KOREAN_MAP.get(englishTopic);
            if (koreanTags == null) {
                return topicCounts.getOrDefault(englishTopic, 0);
            }

            int total = 0;
            for (String koreanTag : koreanTags) {
                total += topicCounts.getOrDefault(koreanTag, 0);
            }
            return total;
        }

        /**
         * 영어 토픽의 한국어 대표 이름 반환
         */
        private String getKoreanTopicName(String englishTopic) {
            List<String> koreanTags = TOPIC_KOREAN_MAP.get(englishTopic);
            return (koreanTags != null && !koreanTags.isEmpty()) ? koreanTags.get(0) : englishTopic;
        }
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
