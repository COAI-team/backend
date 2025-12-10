package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.config.PromptManager;

import kr.or.kosa.backend.codenose.dto.RagDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG(Retrieval-Augmented Generation) 서비스 (RagService)
 * 
 * 역할:
 * 분석된 코드를 벡터 DB(Qdrant 등)에 저장하고, 유사도 검색을 통해 관련 문맥을 찾아냅니다.
 * "내 코드"를 이해하는 맞춤형 답변을 생성하는 핵심 모듈입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

        private final VectorStore vectorStore;
        private final ChatClient.Builder chatClientBuilder;
        private final PromptManager promptManager;

        /**
         * 코드 분석 결과 벡터화 및 저장 (Ingest)
         * 
         * 분석된 코드 내용, 메타데이터, AI 분석 요약을 결합하여 하나의 문서(Document)로 만듭니다.
         * 이를 벡터 스토어에 저장하여 나중에 검색 가능하게 만듭니다.
         * 
         * @param request 사용자 ID, 코드, 분석 결과 등을 담은 요청 객체
         */
        public void ingestCode(RagDto.IngestRequest request) {
                log.info("RAG 데이터 수집 시작 - userId: {}", request.getUserId());

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

                String format = promptManager.getPrompt("RAG_INGEST_FORMAT");
                // RAG_INGEST_FORMAT 포맷에 맞춰 데이터 삽입
                // (Timestamp, File Name, Analysis Summary, Code Content, Full Analysis)
                String content = String.format(
                                format,
                                timestamp,
                                request.getProblemTitle(),
                                "See Analysis below",
                                request.getCode(),
                                request.getAnalysis());

                // 검색 효율성을 높이기 위한 메타데이터 태깅
                Map<String, Object> metadata = Map.of(
                                "userId", request.getUserId(),
                                "language", request.getLanguage() != null ? request.getLanguage() : "unknown",
                                "problemTitle",
                                request.getProblemTitle() != null ? request.getProblemTitle() : "unknown",
                                "timestamp", timestamp,
                                "majorChanges", request.getMajorChanges() != null ? request.getMajorChanges() : "",
                                "desiredAnalysis",
                                request.getDesiredAnalysis() != null ? request.getDesiredAnalysis() : "",
                                "analysisId", request.getAnalysisId() != null ? request.getAnalysisId() : "");

                Document document = new Document(content, metadata);
                vectorStore.add(List.of(document));
                log.info("VectorDB 저장 완료 (Metadata 포함)");
        }

        /**
         * 개인화된 피드백 생성 (Retrieve & Generate)
         * 
         * 사용자의 질문을 받아, 벡터 DB에서 가장 유사한 과거 코드나 분석 이력을 찾아냅니다.
         * 이를 "Context"로 프롬프트에 추가하여, LLM이 문맥을 파악하고 답변하도록 합니다.
         * 
         * @param request 질문 내용 및 사용자 ID
         * @return AI의 답변
         */
        public String getPersonalizedFeedback(RagDto.FeedbackRequest request) {
                log.info("개인화 피드백 생성 - userId: {}", request.getUserId());

                ChatClient chatClient = chatClientBuilder.build();

                // 1. 해당 사용자의 데이터만 검색하도록 필터 설정
                String filterExpression = String.format("userId == '%s'", request.getUserId());

                // 2. 벡터 유사도 검색 (Similarity Search)
                // 질문과 관련된 상위 5개의 문서를 찾아옵니다.
                SearchRequest searchRequest = SearchRequest.builder()
                                .query(request.getQuestion())
                                .topK(5)
                                .filterExpression(filterExpression)
                                .build();

                List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

                if (similarDocuments.isEmpty()) {
                        return "분석 이력을 찾을 수 없습니다. 먼저 코드를 제출하고 분석을 받아보세요.";
                }

                // 3. 검색된 문서들을 하나의 문자열 컨텍스트로 결합
                String context = similarDocuments.stream()
                                .map(Document::getText)
                                .collect(Collectors.joining("\n\n---\n\n"));

                // 4. 프롬프트 생성
                String template = promptManager.getPrompt("RAG_FEEDBACK_PROMPT");
                String prompt = String.format(
                                template,
                                "General Q&A", // Focus Areas
                                "Helpful Mentor", // Tone Intensity
                                "Answer the user's question based on history.", // User Instructions
                                context); // History provided here

                // 5. 최종 LLM 호출
                String finalPrompt = prompt + "\n\nUser Question: " + request.getQuestion();

                return chatClient.prompt()
                                .user(finalPrompt)
                                .call()
                                .content();
        }

        /**
         * 사용자 컨텍스트(과거 실수 패턴 등) 조회용 헬퍼 메서드
         * AnalysisService 등에서 분석 시 문맥을 보강하기 위해 호출합니다.
         */
        public String retrieveUserContext(String userId) {
                try {
                        String filterExpression = String.format("userId == '%s'", userId);

                        // "mistakes", "patterns", "errors" 키워드로 관련 문서 검색
                        SearchRequest searchRequest = SearchRequest.builder()
                                        .query("mistakes patterns errors improvement")
                                        .topK(3)
                                        .filterExpression(filterExpression)
                                        .build();

                        List<Document> documents = vectorStore.similaritySearch(searchRequest);

                        if (documents.isEmpty()) {
                                return "No prior analysis history found.";
                        }

                        return documents.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n\n---\n\n"));
                } catch (Exception e) {
                        log.error("컨텍스트 조회 실패 userId: {}", userId, e);
                        return "Failed to retrieve history.";
                }
        }
}
