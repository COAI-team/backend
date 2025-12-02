package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.RagDto;
import kr.or.kosa.backend.codenose.dto.dtoReal.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.GithubFileDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final RagService ragService; // Injected

    @Autowired
    public AnalysisService(
            ChatClient.Builder chatClientBuilder,
            AnalysisMapper analysisMapper,
            ObjectMapper objectMapper,
            RagService ragService) {
        this.chatClient = chatClientBuilder.build();
        this.analysisMapper = analysisMapper;
        this.objectMapper = objectMapper;
        this.ragService = ragService;
    }

    private final String systemPrompt = """
            당신은 코드 분석 전문가입니다.
            사용자가 제공한 코드를 분석하고 개선점을 제시해주세요.
            """;

    /**
     * 코드 분석 수행 (간단 버전)
     * 
     * @param userId      사용자 ID
     * @param userMessage 사용자 메시지
     * @param requestDto  분석 요청 DTO
     * @return AI 분석 결과
     */
    public String analyzeCode(String userId, String userMessage, AnalysisRequestDTO requestDto) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemPromptTemplate(systemPrompt).createMessage());
            messages.add(new UserMessage(userMessage));

            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

            String aiResponseContent = response.getResult()
                    .getOutput()
                    .getText();

            // DB에 저장
            saveAnalysis(requestDto, aiResponseContent);

            // RAG VectorDB에 저장
            try {
                RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                        userId,
                        requestDto.getCode(),
                        aiResponseContent,
                        "unknown", // Language detection needed or pass from frontend
                        "Direct Analysis",
                        "Direct Code Input",
                        requestDto.getCustomRequirements());
                ragService.ingestCode(ingestRequest);
            } catch (Exception e) {
                log.error("Failed to ingest code to RAG system", e);
                // Don't fail the main request if RAG ingestion fails
            }

            return aiResponseContent;

        } catch (Exception e) {
            log.error("Error during code analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze code.", e);
        }
    }

    /**
     * 저장된 GitHub 파일을 조회하여 AI 분석 수행
     * 
     * @param requestDto 분석 요청 DTO
     * @return AI 분석 결과 (JSON 문자열)
     */
    public String analyzeStoredFile(AnalysisRequestDTO requestDto) {
        try {
            // 1. DB에서 저장된 GitHub 파일 내용 조회
            GithubFileDTO storedFile = analysisMapper.findFileById(requestDto.getAnalysisId());

            if (storedFile == null) {
                throw new RuntimeException("저장된 파일을 찾을 수 없습니다. repositoryUrl: "
                        + ", filePath: " + requestDto.getFilePath() + ", analysisId: " + requestDto.getAnalysisId());
            }

            log.info("파일 조회 완료 - fileId: {}, fileName: {}, contentLength: {}",
                    storedFile.getFileId(),
                    storedFile.getFileName(),
                    storedFile.getFileContent().length());

            // 2. 사용자 컨텍스트 조회 (RAG)
            String userContext = ragService.retrieveUserContext(String.valueOf(requestDto.getUserId()));
            log.info("Retrieved users context for analysis: {}",
                    userContext.substring(0, Math.min(userContext.length(), 100)) + "...");

            // 3. 프롬프트 생성 (toneLevel에 따른 시스템 프롬프트 + Users Context)
            String systemPromptWithTone = PromptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements(),
                    userContext);

            // 4. AI 메시지 구성 (저장된 파일 내용 사용)
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPromptWithTone));
            messages.add(new UserMessage("다음 코드를 분석해주세요:\n\n" + storedFile.getFileContent()));

            // 5. AI 호출
            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String aiResponseContent = response.getResult().getOutput().getText();

            String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);
            System.out.println(aiResponseContent);

            // 5. 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
            String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse);

            // 6. 사용자 코드 패턴 업데이트
            updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(cleanedResponse).path("codeSmells"));

            log.info("AI 분석 완료 - analysisId: {}, fileId: {}, toneLevel: {}",
                    analysisId, storedFile.getFileId(), requestDto.getToneLevel());

            // 7. RAG VectorDB에 저장
            try {
                RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                        String.valueOf(requestDto.getUserId()),
                        storedFile.getFileContent(),
                        cleanedResponse,
                        storedFile.getFileName().substring(storedFile.getFileName().lastIndexOf(".") + 1), // Simple
                                                                                                           // extension
                                                                                                           // check
                        storedFile.getFilePath(),
                        "Stored File Analysis",
                        requestDto.getCustomRequirements());
                ragService.ingestCode(ingestRequest);
            } catch (Exception e) {
                log.error("Failed to ingest stored file analysis to RAG system", e);
            }

            return cleanedResponse;

        } catch (Exception e) {
            log.error("파일 분석 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 분석에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
     * 
     * @param storedFile        GitHub 파일 정보
     * @param requestDto        분석 요청 정보
     * @param aiResponseContent AI 분석 결과
     * @return 저장된 analysisId
     */
    private String saveAnalysisResult(GithubFileDTO storedFile, AnalysisRequestDTO requestDto,
            String aiResponseContent) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponseContent);

            CodeResultDTO result = new CodeResultDTO();
            result.setAnalysisId(UUID.randomUUID().toString());
            result.setUserId(requestDto.getUserId());
            result.setRepositoryUrl(storedFile.getRepositoryUrl());
            result.setFilePath(storedFile.getFilePath());
            result.setAnalysisType(String.join(", ", requestDto.getAnalysisTypes()));
            result.setToneLevel(requestDto.getToneLevel());
            result.setCustomRequirements(requestDto.getCustomRequirements());
            result.setAnalysisResult(aiResponseContent);
            result.setAiScore(jsonNode.path("aiScore").asInt(-1));
            result.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            result.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));
            result.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

            analysisMapper.saveCodeResult(result);

            log.info("분석 결과 저장 완료 - analysisId: {}", result.getAnalysisId());

            return result.getAnalysisId();

        } catch (Exception e) {
            log.error("분석 결과 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("분석 결과 저장에 실패했습니다.", e);
        }
    }

    /**
     * 분석 결과를 DB에 저장 (기존 analyzeCode 메서드용)
     */
    private void saveAnalysis(AnalysisRequestDTO requestDto, String aiResponseContent) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponseContent);

            CodeResultDTO history = new CodeResultDTO();
            history.setAnalysisId(UUID.randomUUID().toString());
            history.setUserId(requestDto.getUserId());
            history.setRepositoryUrl(requestDto.getRepositoryUrl());
            history.setFilePath(requestDto.getFilePath());
            history.setAnalysisType(String.join(", ", requestDto.getAnalysisTypes()));
            history.setToneLevel(requestDto.getToneLevel());
            history.setCustomRequirements(requestDto.getCustomRequirements());
            history.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
            history.setAnalysisResult(aiResponseContent);
            history.setAiScore(jsonNode.path("aiScore").asInt(-1));
            history.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            history.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));

            analysisMapper.saveCodeResult(history);

            // 사용자 코드 패턴 업데이트
            updateUserPatterns(requestDto.getUserId(), jsonNode.path("codeSmells"));

        } catch (Exception e) {
            log.error("Failed to save analysis history and patterns: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save analysis results.", e);
        }
    }

    /**
     * 사용자 코드 패턴 업데이트
     */
    private void updateUserPatterns(Long userId, JsonNode codeSmellsNode) {
        if (codeSmellsNode == null || !codeSmellsNode.isArray()) {
            return;
        }

        for (JsonNode smellNode : codeSmellsNode) {
            String patternType = smellNode.path("name").asText();
            if (patternType.isEmpty()) {
                continue;
            }

            UserCodePatternDTO existingPattern = analysisMapper.findUserCodePattern(userId, patternType);

            if (existingPattern != null) {
                // 기존 패턴 업데이트
                existingPattern.setFrequency(existingPattern.getFrequency() + 1);
                existingPattern.setLastDetected(new Timestamp(System.currentTimeMillis()));
                analysisMapper.updateUserCodePattern(existingPattern);

                log.debug("패턴 업데이트 - userId: {}, patternType: {}, frequency: {}",
                        userId, patternType, existingPattern.getFrequency());
            } else {
                // 새로운 패턴 저장
                UserCodePatternDTO newPattern = new UserCodePatternDTO();
                newPattern.setPatternId(UUID.randomUUID().toString());
                newPattern.setUserId(userId);
                newPattern.setPatternType(patternType);
                newPattern.setFrequency(1);
                newPattern.setLastDetected(new Timestamp(System.currentTimeMillis()));
                newPattern.setImprovementStatus("Detected");
                analysisMapper.saveUserCodePattern(newPattern);

                log.debug("새 패턴 저장 - userId: {}, patternType: {}", userId, patternType);
            }
        }
    }

    /**
     * 사용자별 분석 결과 조회
     */
    public List<CodeResultDTO> getUserAnalysisHistory(Long userId) {
        return analysisMapper.findCodeResultByUserId(userId);
    }

    /**
     * 특정 분석 결과 조회
     */
    public CodeResultDTO getAnalysisResult(String analysisId) {
        return analysisMapper.findCodeResultById(analysisId);
    }

    /**
     * 사용자 코드 패턴 조회
     */
    public List<UserCodePatternDTO> getUserPatterns(Long userId) {
        return analysisMapper.findAllPatternsByUserId(userId);
    }

    /**
     * 저장된 GitHub 파일을 조회하여 AI 분석 수행 (스트리밍 버전)
     *
     * @param requestDto 분석 요청 DTO
     * @return AI 분석 결과 스트림 (Flux<String>)
     */
    public reactor.core.publisher.Flux<String> analyzeStoredFileStream(AnalysisRequestDTO requestDto) {
        try {
            // 1. DB에서 저장된 GitHub 파일 내용 조회
            GithubFileDTO storedFile = analysisMapper.findFileById(requestDto.getAnalysisId());

            if (storedFile == null) {
                throw new RuntimeException("저장된 파일을 찾을 수 없습니다. repositoryUrl: "
                        + ", filePath: " + requestDto.getFilePath() + ", analysisId: " + requestDto.getAnalysisId());
            }

            log.info("파일 조회 완료 (스트림) - fileId: {}, fileName: {}, contentLength: {}",
                    storedFile.getFileId(),
                    storedFile.getFileName(),
                    storedFile.getFileContent().length());

            // 2. 사용자 컨텍스트 조회 (RAG)
            String userContext = ragService.retrieveUserContext(String.valueOf(requestDto.getUserId()));

            // 3. 프롬프트 생성
            String systemPromptWithTone = PromptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements(),
                    userContext);

            // 4. AI 메시지 구성
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPromptWithTone));
            messages.add(new UserMessage("다음 코드를 분석해주세요:\n\n" + storedFile.getFileContent()));

            // 5. AI 호출 및 스트리밍
            Prompt prompt = new Prompt(messages);
            StringBuilder accumulatedContent = new StringBuilder();

            return chatClient.prompt(prompt)
                    .stream()
                    .chatResponse()
                    .map(response -> {
                        String content = response.getResult().getOutput().getText();
                        if (content != null) {
                            accumulatedContent.append(content);
                            return content;
                        }
                        return "";
                    })
                    .doOnComplete(() -> {
                        // 스트림 완료 후 DB 저장 및 후처리
                        String fullContent = accumulatedContent.toString();
                        String cleanedResponse = cleanMarkdownCodeBlock(fullContent);
                        log.info("스트림 분석 완료. 결과 저장 시작.");

                        try {
                            // 5. 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
                            String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse);

                            // 6. 사용자 코드 패턴 업데이트
                            updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(cleanedResponse).path("codeSmells"));

                            // 7. RAG VectorDB에 저장
                            RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                                    String.valueOf(requestDto.getUserId()),
                                    storedFile.getFileContent(),
                                    cleanedResponse,
                                    storedFile.getFileName().substring(storedFile.getFileName().lastIndexOf(".") + 1),
                                    storedFile.getFilePath(),
                                    "Stored File Analysis (Stream)",
                                    requestDto.getCustomRequirements());
                            ragService.ingestCode(ingestRequest);

                            log.info("스트림 분석 결과 저장 완료 - analysisId: {}", analysisId);

                        } catch (Exception e) {
                            log.error("스트림 분석 결과 저장 실패", e);
                        }
                    })
                    .doOnError(e -> log.error("스트림 분석 중 오류 발생", e));

        } catch (Exception e) {
            log.error("파일 분석 스트림 시작 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 분석 스트림 시작에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * AI 응답에서 JSON 부분만 추출
     * 
     * @param response AI 원본 응답
     * @return 정제된 JSON 문자열
     */
    private String cleanMarkdownCodeBlock(String response) {
        if (response == null) {
            return "{}";
        }

        String cleaned = response.trim();

        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }

        return cleaned;
    }
}