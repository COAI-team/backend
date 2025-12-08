package kr.or.kosa.backend.codenose_withoutRAG.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.dtoReal.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.GithubFileDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import kr.or.kosa.backend.codenose.service.PromptGenerator;
import kr.or.kosa.backend.codenose.service.PromptManager;
import kr.or.kosa.backend.codenose.service.agent.AgenticWorkflowService;
import kr.or.kosa.backend.codenose.service.pipeline.PipelineContext;
import kr.or.kosa.backend.codenose.service.pipeline.StyleExtractorModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
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
public class AnalysisWithoutRagService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final AgenticWorkflowService agenticWorkflowService;
    private final StyleExtractorModule styleExtractorModule;
    private final PromptManager promptManager;
    private final PromptGenerator promptGenerator;

    @Autowired
    public AnalysisWithoutRagService(
            ChatClient.Builder chatClientBuilder,
            AnalysisMapper analysisMapper,
            ObjectMapper objectMapper,
            AgenticWorkflowService agenticWorkflowService,
            StyleExtractorModule styleExtractorModule,
            PromptManager promptManager,
            PromptGenerator promptGenerator) {
        this.chatClient = chatClientBuilder.build();
        this.analysisMapper = analysisMapper;
        this.objectMapper = objectMapper;
        this.agenticWorkflowService = agenticWorkflowService;
        this.styleExtractorModule = styleExtractorModule;
        this.promptManager = promptManager;
        this.promptGenerator = promptGenerator;
    }

    /**
     * 코드 분석 수행 (간단 버전) - RAG 제외
     */
    public String analyzeCode(String userId, String userMessage, AnalysisRequestDTO requestDto) {
        try {
            String systemPrompt = promptManager.getPrompt("SIMPLE_ANALYSIS_PROMPT");
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemPromptTemplate(systemPrompt).createMessage());
            messages.add(new UserMessage(userMessage));

            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

            String aiResponseContent = response.getResult()
                    .getOutput()
                    .getText();

            // DB에 저장 (RAG 저장 로직 제거)
            saveAnalysis(requestDto, aiResponseContent);

            return aiResponseContent;

        } catch (Exception e) {
            log.error("Error during code analysis (No RAG): {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze code (No RAG).", e);
        }
    }

    /**
     * 저장된 GitHub 파일을 조회하여 AI 분석 수행 - RAG 제외
     */
    public String analyzeStoredFile(AnalysisRequestDTO requestDto) {
        try {
            // 1. DB에서 저장된 GitHub 파일 내용 조회
            GithubFileDTO storedFile = analysisMapper.findFileById(requestDto.getAnalysisId());

            if (storedFile == null) {
                throw new RuntimeException("저장된 파일을 찾을 수 없습니다. repositoryUrl: "
                        + ", filePath: " + requestDto.getFilePath() + ", analysisId: " + requestDto.getAnalysisId());
            }

            log.info("파일 조회 완료 (No RAG) - fileId: {}, fileName: {}, contentLength: {}",
                    storedFile.getFileId(),
                    storedFile.getFileName(),
                    storedFile.getFileContent().length());

            // 2. 사용자 컨텍스트 조회 (RAG 제외: 빈 컨텍스트 사용)
            String userContext = "No prior history available (RAG Disabled).";
            log.info("Using empty context for analysis (No RAG)");

            // 3. 프롬프트 생성
            String systemPromptWithTone = promptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements(),
                    userContext);

            // 4. Agentic Workflow 실행
            String aiResponseContent = agenticWorkflowService.executeWorkflow(storedFile.getFileContent(),
                    systemPromptWithTone);

            String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);
            System.out.println(aiResponseContent);

            // 5. 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
            String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse);

            // 6. 사용자 코드 패턴 업데이트
            updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(cleanedResponse).path("codeSmells"));

            log.info("AI 분석 완료 (No RAG) - analysisId: {}, fileId: {}, toneLevel: {}",
                    analysisId, storedFile.getFileId(), requestDto.getToneLevel());

            // 7. RAG VectorDB 저장 로직 제거

            return cleanedResponse;

        } catch (Exception e) {
            log.error("파일 분석 실패 (No RAG): {}", e.getMessage(), e);
            throw new RuntimeException("파일 분석에 실패했습니다 (No RAG): " + e.getMessage());
        }
    }

    /**
     * 저장된 GitHub 파일을 조회하여 AI 분석 수행 (스트리밍 버전) - RAG 제외
     */
    public reactor.core.publisher.Flux<String> analyzeStoredFileStream(AnalysisRequestDTO requestDto) {
        System.out.println(
                "[TRACE] AnalysisWithoutRagService.analyzeStoredFileStream called with requestDto: " + requestDto);
        return reactor.core.publisher.Mono.fromCallable(() -> {
            try {
                // 1. DB에서 저장된 GitHub 파일 내용 조회
                GithubFileDTO storedFile = analysisMapper.findFileById(requestDto.getAnalysisId());

                if (storedFile == null) {
                    throw new RuntimeException("저장된 파일을 찾을 수 없습니다. repositoryUrl: "
                            + ", filePath: " + requestDto.getFilePath() + ", analysisId: "
                            + requestDto.getAnalysisId());
                }

                log.info("파일 조회 완료 (스트림, No RAG) - fileId: {}, fileName: {}, contentLength: {}",
                        storedFile.getFileId(),
                        storedFile.getFileName(),
                        storedFile.getFileContent().length());

                // 2. 사용자 컨텍스트 조회 (RAG 제외)
                String userContext = "No prior history available (RAG Disabled).";

                // 3. 스타일 추출 (Pipeline) - 스타일 추출은 유지 (RAG와 무관한 경우)
                // 하지만 스타일 추출도 이전 기록을 참조한다면 제외해야 할 수도 있음.
                // 여기서는 "RAG를 제외한 pipeline"이라고 했으므로, VectorDB 검색을 통한 컨텍스트만 제외.
                // StyleExtractorModule이 내부적으로 무엇을 하는지 확인 필요하지만, 일단 유지.
                // 만약 StyleExtractorModule이 RAG를 쓴다면 이것도 빈 값으로 처리해야 함.
                // StyleExtractorModule은 userContext를 입력으로 받으므로, userContext가 비어있으면 스타일도 추출되지 않을
                // 것임.

                PipelineContext pipelineContext = PipelineContext.builder()
                        .userContext(userContext)
                        .build();
                pipelineContext = styleExtractorModule.extractStyle(pipelineContext);
                String styleRules = pipelineContext.getStyleRules();
                log.info("Extracted Style Rules (No RAG): {}", styleRules);

                // 4. 프롬프트 생성
                String customReqWithStyle = requestDto.getCustomRequirements() + "\n\n[Style Rules]\n" + styleRules;
                String systemPromptWithTone = promptGenerator.createSystemPrompt(
                        requestDto.getAnalysisTypes(),
                        requestDto.getToneLevel(),
                        customReqWithStyle,
                        userContext);

                // 5. Agentic Workflow 실행
                log.info("Starting Agentic Workflow for Stream (No RAG)...");
                String aiResponseContent = agenticWorkflowService.executeWorkflow(storedFile.getFileContent(),
                        systemPromptWithTone);

                // 6. 결과 저장 및 후처리
                String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);

                // DB 저장
                String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse);

                // 패턴 업데이트
                updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(cleanedResponse).path("codeSmells"));

                // RAG 저장 로직 제거

                return aiResponseContent;

            } catch (Exception e) {
                log.error("Error in Agentic Workflow Stream (No RAG)", e);
                throw new RuntimeException(e);
            }
        }).flatMapMany(result -> reactor.core.publisher.Flux.just(result));
    }

    // Helper methods (copied from AnalysisService)

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
            return result.getAnalysisId();
        } catch (Exception e) {
            log.error("분석 결과 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("분석 결과 저장에 실패했습니다.", e);
        }
    }

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
            updateUserPatterns(requestDto.getUserId(), jsonNode.path("codeSmells"));
        } catch (Exception e) {
            log.error("Failed to save analysis history: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save analysis results.", e);
        }
    }

    private void updateUserPatterns(Long userId, JsonNode codeSmellsNode) {
        if (codeSmellsNode == null || !codeSmellsNode.isArray()) {
            return;
        }
        for (JsonNode smellNode : codeSmellsNode) {
            String patternType = smellNode.path("name").asText();
            if (patternType.isEmpty())
                continue;
            UserCodePatternDTO existingPattern = analysisMapper.findUserCodePattern(userId, patternType);
            if (existingPattern != null) {
                existingPattern.setFrequency(existingPattern.getFrequency() + 1);
                existingPattern.setLastDetected(new Timestamp(System.currentTimeMillis()));
                analysisMapper.updateUserCodePattern(existingPattern);
            } else {
                UserCodePatternDTO newPattern = new UserCodePatternDTO();
                newPattern.setPatternId(UUID.randomUUID().toString());
                newPattern.setUserId(userId);
                newPattern.setPatternType(patternType);
                newPattern.setFrequency(1);
                newPattern.setLastDetected(new Timestamp(System.currentTimeMillis()));
                newPattern.setImprovementStatus("Detected");
                analysisMapper.saveUserCodePattern(newPattern);
            }
        }
    }

    private String cleanMarkdownCodeBlock(String response) {
        if (response == null)
            return "{}";
        String cleaned = response.trim();
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }
        return cleaned;
    }
}
