package kr.or.kosa.backend.codenose.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

        private final VectorStore vectorStore;
        private final ChatClient.Builder chatClientBuilder;
        private final PromptManager promptManager;

        public void ingestCode(RagDto.IngestRequest request) {
                System.out.println("[TRACE] RagService.ingestCode called with request: " + request);
                log.info("Ingesting code for users: {}", request.getUserId());

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

                String format = promptManager.getPrompt("RAG_INGEST_FORMAT");
                // RAG_INGEST_FORMAT expects: Timestamp, File, Violated Commandments, Context
                // Snippet, Previous Analysis
                // We don't have "Violated Commandments" directly, so we pass "See Analysis" or
                // extract it if possible.
                // For now, passing "See Analysis" to avoid format error.
                String content = String.format(
                                format,
                                timestamp,
                                request.getProblemTitle(),
                                "See Analysis below", // Placeholder for Violated Commandments
                                request.getCode(),
                                request.getAnalysis());

                Map<String, Object> metadata = Map.of(
                                "userId", request.getUserId(),
                                "language", request.getLanguage() != null ? request.getLanguage() : "unknown",
                                "problemTitle",
                                request.getProblemTitle() != null ? request.getProblemTitle() : "unknown",
                                "timestamp", timestamp,
                                "majorChanges", request.getMajorChanges() != null ? request.getMajorChanges() : "",
                                "desiredAnalysis",
                                request.getDesiredAnalysis() != null ? request.getDesiredAnalysis() : "",
                                "analysisId",
                                request.getAnalysisId() != null ? request.getAnalysisId() : "");

                Document document = new Document(content, metadata);
                vectorStore.add(List.of(document));
                log.info("Successfully saved code analysis to VectorDB with metadata");
        }

        public String getPersonalizedFeedback(RagDto.FeedbackRequest request) {
                System.out.println("[TRACE] RagService.getPersonalizedFeedback called with request: " + request);
                log.info("Generating feedback for users: {}", request.getUserId());

                ChatClient chatClient = chatClientBuilder.build();

                // Search for relevant documents for this users
                String filterExpression = String.format("userId == '%s'", request.getUserId());

                // We fetch a bit more documents to allow for in-context prioritization of
                // recent ones
                SearchRequest searchRequest = SearchRequest.builder()
                                .query(request.getQuestion())
                                .topK(5) // Increased to get more context
                                .filterExpression(filterExpression)
                                .build();

                List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

                if (similarDocuments.isEmpty()) {
                        return "No previous code history found for this users. Please submit some code first.";
                }

                // Sort documents by timestamp if possible, or just rely on the LLM to read the
                // timestamp in the content
                // Here we just join them. The content already contains "Timestamp: ..." at the
                // top.
                String context = similarDocuments.stream()
                                .map(Document::getText)
                                .collect(Collectors.joining("\n\n---\n\n"));

                String template = promptManager.getPrompt("RAG_FEEDBACK_PROMPT");
                // RAG_FEEDBACK_PROMPT expects: Focus Areas, Tone Intensity, User Instructions,
                // History
                // We provide default values for the first 3 since this is a chat context.
                String prompt = String.format(
                                template,
                                "General Q&A", // Focus Areas
                                "Helpful Mentor", // Tone Intensity
                                "Answer the user's question based on history.", // User Instructions
                                context); // History

                // Append the user question at the end as a UserMessage or part of the prompt?
                // The template ends with "This is the ONLY code you must analyze right now:",
                // which seems wrong for Q&A.
                // But we must follow the template. We'll append the question to the prompt or
                // send it as a separate message.
                // Since we are using chatClient.prompt().user(prompt), we can just append the
                // question.

                String finalPrompt = prompt + "\n\nUser Question: " + request.getQuestion();

                return chatClient.prompt()
                                .user(finalPrompt)
                                .call()
                                .content();
        }

        /**
         * Retrieves users context (past mistakes, patterns) for RAG-enhanced analysis.
         */
        public String retrieveUserContext(String userId) {
                System.out.println("[TRACE] RagService.retrieveUserContext called with userId: " + userId);
                try {
                        System.out.println("*****Attempting to retrieve context for userId: {}*****" + userId);
                        String filterExpression = String.format("userId == '%s'", userId);
                        System.out.println("*****Using filter expression: {}*****" + filterExpression);

                        // Search for general "mistakes" or "patterns" or just get recent ones
                        SearchRequest searchRequest = SearchRequest.builder()
                                        .query("mistakes patterns errors improvement")
                                        .topK(3)
                                        .filterExpression(filterExpression)
                                        .build();

                        List<Document> documents = vectorStore.similaritySearch(searchRequest);
                        System.out.println("*****Found {} documents for user context*****" + documents.size());

                        if (documents.isEmpty()) {
                                System.out.println("*****No documents found, returning default message.*****");
                                return "No prior analysis history found.";
                        }

                        return documents.stream()
                                        .map(Document::getText)
                                        .collect(Collectors.joining("\n\n---\n\n"));
                } catch (Exception e) {
                        System.out.println("*****Failed to retrieve user context for userId: {}*****" + userId);
                        return "Failed to retrieve history.";
                }
        }
}
