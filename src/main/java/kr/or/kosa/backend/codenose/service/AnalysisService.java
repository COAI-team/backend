package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.RagDto;
import kr.or.kosa.backend.codenose.dto.dtoReal.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.GithubFileDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import kr.or.kosa.backend.codenose.service.agent.AgenticWorkflowService;

import kr.or.kosa.backend.codenose.service.search.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final RagService ragService;
    private final HybridSearchService hybridSearchService;
    private final AgenticWorkflowService agenticWorkflowService;

    private final PromptManager promptManager;
    private final PromptGenerator promptGenerator;

    @Autowired
    public AnalysisService(
            ChatClient.Builder chatClientBuilder,
            AnalysisMapper analysisMapper,
            ObjectMapper objectMapper,
            RagService ragService,
            HybridSearchService hybridSearchService,
            AgenticWorkflowService agenticWorkflowService,
            PromptManager promptManager,
            PromptGenerator promptGenerator) {
        this.chatClient = chatClientBuilder.build();
        this.analysisMapper = analysisMapper;
        this.objectMapper = objectMapper;
        this.ragService = ragService;
        this.hybridSearchService = hybridSearchService;

        this.agenticWorkflowService = agenticWorkflowService;
        this.promptManager = promptManager;
        this.promptGenerator = promptGenerator;
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

            // 2. 사용자 컨텍스트 조회 (Hybrid Search)
            // Using Hybrid Search to get relevant context documents
            String language = getLanguageFromExtension(storedFile.getFileName());
            List<org.springframework.ai.document.Document> contextDocs = hybridSearchService.search(
                    "mistakes patterns errors improvement",
                    storedFile.getFileContent(),
                    3,
                    language);

            String userContext = contextDocs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));

            // Capture related analysis IDs from context documents
            List<Map<String, String>> relatedIds = contextDocs.stream()
                    .map(doc -> {
                        Map<String, Object> meta = doc.getMetadata();
                        String id = (String) meta.get("analysisId");
                        if (id != null && !id.isEmpty()) {
                            return Map.of(
                                    "id", id,
                                    "timestamp", (String) meta.getOrDefault("timestamp", ""),
                                    "fileName", (String) meta.getOrDefault("problemTitle", "Unknown File"));
                        }
                        return null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();

            String relatedAnalysisIdsJson = "[]";
            try {
                relatedAnalysisIdsJson = objectMapper.writeValueAsString(relatedIds);
            } catch (Exception e) {
                log.error("Failed to serialize related analysis IDs", e);
            }

            if (userContext.isEmpty()) {
                userContext = "No prior history available.";
            }

            log.info("Retrieved users context for analysis (Hybrid): {}",
                    userContext.substring(0, Math.min(userContext.length(), 100)) + "...");

            // 3. 프롬프트 생성 (toneLevel에 따른 시스템 프롬프트 + Users Context)
            String systemPromptWithTone = promptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements(),
                    userContext);

            // 3.1. 메타데이터 추출 (Metadata Extraction)
            String metadataPrompt = promptGenerator.createMetadataPrompt(storedFile.getFileContent());
            String metadataJson = extractMetadata(metadataPrompt);
            log.info("Metadata extracted: {}", metadataJson != null ? "Success" : "Failed");

            // 4. Agentic Workflow 실행
            // Instead of direct ChatClient call, we use the Agentic Workflow
            String aiResponseContent = agenticWorkflowService.executeWorkflow(storedFile.getFileContent(),
                    systemPromptWithTone);

            String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);
            System.out.println(aiResponseContent);

            // 5. 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
            String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse, metadataJson,
                    relatedAnalysisIdsJson);

            // 6. 사용자 코드 패턴 업데이트
            updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(cleanedResponse).path("codeSmells"));

            log.info("AI 분석 완료 - analysisId: {}, fileId: {}, toneLevel: {}",
                    analysisId, storedFile.getFileId(), requestDto.getToneLevel());

            // 7. RAG VectorDB에 저장
            try {
                RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                        String.valueOf(requestDto.getUserId()),
                        metadataJson, // content (Now storing Metadata instead of raw code)
                        cleanedResponse,
                        storedFile.getFileName().substring(storedFile.getFileName().lastIndexOf(".") + 1), // Simple
                                                                                                           // extension
                                                                                                           // check
                        storedFile.getFilePath(),
                        "Stored File Metadata",
                        requestDto.getCustomRequirements(),
                        analysisId); // Pass analysisId
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
            String aiResponseContent, String metadataJson, String relatedAnalysisIdsJson) {
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
            System.out.println(jsonNode.path("aiScore").asInt(-1));
            result.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            System.out.println(jsonNode.path("codeSmells"));
            result.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));
            System.out.println(jsonNode.path("suggestions"));
            result.setMetadata(metadataJson);
            result.setRelatedAnalysisIds(relatedAnalysisIdsJson);
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
     * 기존 분석 결과에 대한 메타데이터 백필 실행
     * (Admin or Migration purpose)
     */
    public int runMetadataBackfill() {
        List<CodeResultDTO> targets = analysisMapper.findAnalysisWithoutMetadata();
        log.info("Found {} analysis records without metadata", targets.size());

        int successCount = 0;
        for (CodeResultDTO result : targets) {
            try {
                // 1. 해당 파일의 내용을 찾음 (GitHub 파일)
                // Result stores repoUrl and filePath.
                // We try to find the LATEST content for this path.
                GithubFileDTO file = analysisMapper.findLatestFileContent(result.getRepositoryUrl(),
                        result.getFilePath());
                if (file == null) {
                    log.warn("File content not found for analysisId: {}", result.getAnalysisId());
                    continue;
                }

                // 2. 메타데이터 생성
                String metadataPrompt = promptGenerator.createMetadataPrompt(file.getFileContent());
                String metadataJson = extractMetadata(metadataPrompt);

                // 3. 업데이트
                analysisMapper.updateAnalysisMetadata(result.getAnalysisId(), metadataJson);
                successCount++;
                log.info("Backfilled metadata for analysisId: {}", result.getAnalysisId());

            } catch (Exception e) {
                log.error("Failed to backfill metadata for analysisId: " + result.getAnalysisId(), e);
            }
        }
        return successCount;
    }

    /**
     * 사용자 코드 패턴 조회
     */
    public List<UserCodePatternDTO> getUserPatterns(Long userId) {
        return analysisMapper.findAllPatternsByUserId(userId);
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

    /**
     * 메타데이터 추출 실행
     */
    private String extractMetadata(String prompt) {
        try {
            String response = chatClient.prompt(prompt).call().content();
            return cleanMarkdownCodeBlock(response);
        } catch (Exception e) {
            log.error("Metadata extraction failed", e);
            // 실패 시 빈 JSON 반환하여 진행은 되도록 함
            return "{}";
        }
    }

    private String getLanguageFromExtension(String fileName) {
        if (fileName == null)
            return "java";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".py"))
            return "python";
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx"))
            return "javascript";
        return "java";
    }
}