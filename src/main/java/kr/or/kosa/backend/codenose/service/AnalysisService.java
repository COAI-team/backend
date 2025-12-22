package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import kr.or.kosa.backend.codenose.aop.LangfuseObserve;
import kr.or.kosa.backend.codenose.dto.RagDto;
import kr.or.kosa.backend.codenose.dto.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.GithubFileDTO;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import kr.or.kosa.backend.codenose.service.agent.AgenticWorkflowService;
import kr.or.kosa.backend.codenose.service.search.HybridSearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

/**
 * 코드 분석 서비스 (AnalysisService)
 * 
 * 역할:
 * 이 프로젝트의 핵심 두뇌 역할을 합니다.
 * 사용자가 요청한 코드를 읽어오고, AI 에이전트를 통해 분석하며, 그 결과를 저장하고,
 * RAG 시스템에 재학습시키는 전체 파이프라인(Pipeline)을 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;
    private final ChatLanguageModel chatLanguageModel;
    private final RagService ragService;
    private final HybridSearchService hybridSearchService;
    private final AgenticWorkflowService agenticWorkflowService;
    private final MistakeService mistakeService;

    // 설정 및 프롬프트 관리
    private final PromptGenerator promptGenerator;
    private final LangfuseService langfuseService;

    @Autowired
    public AnalysisService(
            ChatLanguageModel chatLanguageModel,
            AnalysisMapper analysisMapper,
            ObjectMapper objectMapper,
            RagService ragService,
            HybridSearchService hybridSearchService,
            AgenticWorkflowService agenticWorkflowService,
            MistakeService mistakeService,
            PromptGenerator promptGenerator,
            LangfuseService langfuseService) {
        this.chatLanguageModel = chatLanguageModel;
        this.analysisMapper = analysisMapper;
        this.objectMapper = objectMapper;
        this.ragService = ragService;
        this.hybridSearchService = hybridSearchService;
        this.mistakeService = mistakeService;

        this.agenticWorkflowService = agenticWorkflowService;
        this.promptGenerator = promptGenerator;
        this.langfuseService = langfuseService;
    }

    /**
     * 저장된 GitHub 파일을 조회하여 AI 분석 수행 (핵심 로직)
     * 
     * 전체 흐름:
     * 1. [DB 조회] 먼저 DB에 저장된 파일 원본을 가져옵니다.
     * 2. [문맥 검색] Hybrid Search를 통해 과거의 유사한 실수나 패턴을 찾아 문맥(Context)으로 활용합니다.
     * 3. [프롬프트] 사용자 요구사항과 문맥을 조합하여 최적의 시스템 프롬프트를 생성합니다.
     * 4. [AI 실행] Agentic Workflow를 실행하여 심층 분석을 수행합니다.
     * 5. [결과 정제] AI의 응답(Markdown)에서 JSON 부분만 깔끔하게 추출합니다.
     * 6. [DB 저장] 분석 결과 및 도출된 Code Smell 패턴을 저장합니다.
     * 7. [RAG 학습] 분석된 내용을 다시 벡터 DB에 저장하여, 시스템이 점점 똑똑해지도록 만듭니다.
     * 
     * @param requestDto 분석 요청 DTO
     * @return AI 분석 결과 (JSON 문자열)
     */
    @LangfuseObserve(name = "analyzeStoredFile")
    public String analyzeStoredFile(AnalysisRequestDTO requestDto) {
        // 워크플로우 시작: Website-RAG-Analysis Trace 생성
        langfuseService.startNamedTrace("Website-RAG-Analysis",
                String.valueOf(requestDto.getUserId()),
                Map.of("mode", "RAG", "fileId", requestDto.getAnalysisId()));

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
            Instant searchStart = Instant.now();
            String language = getLanguageFromExtension(storedFile.getFileName());

            // 내부 스팬 시작 (Manual Instrument - Granular Trace)
            langfuseService.startSpan("HybridSearch", searchStart, Map.of("query", "mistakes patterns"));

            List<org.springframework.ai.document.Document> contextDocs = hybridSearchService.search(
                    "mistakes patterns errors improvement",
                    storedFile.getFileContent(),
                    3,
                    language);

            langfuseService.endSpan(null, Instant.now(), Collections.emptyMap()); // HybridSearch 스팬 종료

            String userContext = contextDocs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));

            // 검색된 문서에서 메타데이터(관련 분석 ID 등)를 추출
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
                log.error("관련 분석 ID 직렬화 실패", e);
            }

            if (userContext.isEmpty()) {
                userContext = "No prior history available.";
            }

            log.info("사용자 컨텍스트 조회 성공 (Hybrid): {}",
                    userContext.substring(0, Math.min(userContext.length(), 100)) + "...");

            // 3. 프롬프트 생성 (시스템 프롬프트 + 사용자 컨텍스트 + 요청사항)

            // 2.5. 코드 스타일 분석 (10가지 기준) - Main Agent에게 "이 유저는 이런 스타일이다"라고 알려주기 위함
            Instant styleStart = Instant.now();
            langfuseService.startSpan("StyleAnalysis", styleStart, Collections.emptyMap());

            String styleJson = analyzeCodeStyle(storedFile.getFileContent());
            log.info("Code Style extracted: {}", styleJson != null ? "Success" : "Failed");

            langfuseService.endSpan(null, Instant.now(), Collections.emptyMap());

            // 3. 프롬프트 생성 (시스템 프롬프트 + 사용자 컨텍스트 + 요청사항 + 스타일)
            Instant promptStart = Instant.now();
            langfuseService.startSpan("PromptGeneration", promptStart, Collections.emptyMap());

            String systemPromptWithTone = promptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements(),
                    userContext,
                    styleJson);

            String metadataPrompt = promptGenerator.createMetadataPrompt(storedFile.getFileContent());

            langfuseService.endSpan(null, Instant.now(), Collections.emptyMap()); // PromptGeneration 스팬 종료

            // 3.1. 메타데이터 별도 추출
            String metadataJson = extractMetadata(metadataPrompt);
            log.info("메타데이터 추출 상태: {}", metadataJson != null ? "성공" : "실패");

            // 4. Agentic Workflow 실행
            Instant genStart = Instant.now();
            langfuseService.startSpan("AgenticWorkflow", genStart, Map.of("model", "gpt-4o"));

            String aiResponseContent = agenticWorkflowService.executeWorkflow(storedFile.getFileContent(),
                    systemPromptWithTone);

            langfuseService.endSpan(null, Instant.now(), Collections.emptyMap()); // AgenticWorkflow 스팬 종료

            String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);

            // 5.5. 스타일 분석 결과를 최종 응답 JSON에 병합 (DB 저장 전)
            if (styleJson != null) {
                try {
                    com.fasterxml.jackson.databind.node.ObjectNode mainNode = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper
                            .readTree(cleanedResponse);
                    JsonNode styleNode = objectMapper.readTree(styleJson);
                    mainNode.set("styleAnalysis", styleNode); // Root에 styleAnalysis 추가
                    cleanedResponse = objectMapper.writeValueAsString(mainNode);
                } catch (Exception e) {
                    log.error("Failed to merge style analysis", e);
                }
            }

            // 5. 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
            String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse, metadataJson,
                    relatedAnalysisIdsJson);

            // 6. 사용자 코드 패턴 업데이트
            JsonNode smellNode = objectMapper.readTree(cleanedResponse).path("codeSmells");
            updateUserPatterns(requestDto.getUserId(), smellNode);

            // 6.5 실수 트래킹
            mistakeService.trackMistakes(requestDto.getUserId(), smellNode);

            log.info("AI 분석 완료 - analysisId: {}, fileId: {}, toneLevel: {}",
                    analysisId, storedFile.getFileId(), requestDto.getToneLevel());

            // 7. RAG VectorDB에 저장 (Ingest)
            try {
                RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                        String.valueOf(requestDto.getUserId()),
                        metadataJson,
                        cleanedResponse,
                        storedFile.getFileName().substring(storedFile.getFileName().lastIndexOf(".") + 1),
                        storedFile.getFilePath(),
                        "Stored File Metadata",
                        requestDto.getCustomRequirements(),
                        analysisId);
                ragService.ingestCode(ingestRequest);
            } catch (Exception e) {
                log.error("RAG 시스템에 분석 결과 저장 실패", e);
            }
            // return cleanedResponse; 기존의 리턴 삭제될 예정

            // 8. analysisId를 응답 JSON에 추가
            try {
                JsonNode resultNode = objectMapper.readTree(cleanedResponse);
                ((com.fasterxml.jackson.databind.node.ObjectNode) resultNode).put("analysisId", analysisId);
                return objectMapper.writeValueAsString(resultNode);
            } catch (Exception e) {
                log.error("analysisId 추가 실패", e);
                return cleanedResponse;
            }

        } catch (Exception e) {
            log.error("파일 분석 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 분석에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 분석 결과를 데이터베이스에 저장
     * 
     * @param storedFile        원본 파일 정보
     * @param requestDto        요청 정보
     * @param aiResponseContent AI 응답 결과
     * @return 생성된 분석 ID
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

            // 점수 및 상세 항목 매핑
            result.setAiScore(jsonNode.path("aiScore").asInt(-1));
            result.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            result.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));

            result.setMetadata(metadataJson);
            result.setRelatedAnalysisIds(relatedAnalysisIdsJson);
            result.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

            analysisMapper.saveCodeResult(result);

            log.info("분석 결과 DB 저장 완료 - analysisId: {}", result.getAnalysisId());

            return result.getAnalysisId();

        } catch (Exception e) {
            log.error("분석 결과 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("분석 결과 저장에 실패했습니다.", e);
        }
    }

    /**
     * 사용자 코드 패턴 업데이트
     * 
     * 발견된 Code Smell을 카운팅하여, 사용자가 자주 범하는 실수를 통계화합니다.
     * 이미 존재하는 패턴이면 빈도(frequency)를 증가시키고, 없으면 새로 생성합니다.
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
                // 기존 패턴 업데이트 (빈도 증가)
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
                newPattern.setImprovementStatus("Detected"); // 초기 상태
                analysisMapper.saveUserCodePattern(newPattern);

                log.debug("새 패턴 저장 - userId: {}, patternType: {}", userId, patternType);
            }
        }
    }

    /**
     * 사용자별 분석 결과 이력 조회
     */
    public List<CodeResultDTO> getUserAnalysisHistory(Long userId) {
        return analysisMapper.findCodeResultByUserId(userId);
    }

    /**
     * 특정 분석 결과 ID로 상세 조회
     */
    public CodeResultDTO getAnalysisResult(String analysisId) {
        return analysisMapper.findCodeResultById(analysisId);
    }

    /**
     * 메타데이터 백필 실행 (관리자용 유틸리티)
     * 
     * 기존에 메타데이터가 없이 분석된 레코드들을 찾아, 다시 메타데이터를 추출하고 채워넣습니다.
     */
    public int runMetadataBackfill() {
        List<CodeResultDTO> targets = analysisMapper.findAnalysisWithoutMetadata();
        log.info("메타데이터 없는 분석 레코드 {}개 발견", targets.size());

        int successCount = 0;
        for (CodeResultDTO result : targets) {
            try {
                // 원본 파일 내용 조회 (최신 버전 기준)
                GithubFileDTO file = analysisMapper.findLatestFileContent(result.getRepositoryUrl(),
                        result.getFilePath());
                if (file == null) {
                    log.warn("파일 내용을 찾을 수 없음 analysisId: {}", result.getAnalysisId());
                    continue;
                }

                // AI를 통해 메타데이터 다시 추출
                String metadataPrompt = promptGenerator.createMetadataPrompt(file.getFileContent());
                String metadataJson = extractMetadata(metadataPrompt);

                // DB 업데이트
                analysisMapper.updateAnalysisMetadata(result.getAnalysisId(), metadataJson);
                successCount++;
                log.info("메타데이터 백필 완료 analysisId: {}", result.getAnalysisId());

            } catch (Exception e) {
                log.error("메타데이터 백필 실패 analysisId: " + result.getAnalysisId(), e);
            }
        }
        return successCount;
    }

    /**
     * 사용자의 모든 코드 패턴 조회
     */
    public List<UserCodePatternDTO> getUserPatterns(Long userId) {
        return analysisMapper.findAllPatternsByUserId(userId);
    }

    /**
     * AI 응답 정제 (Markdown 코드 블록 제거)
     * 
     * AI가 ```json ... ``` 형태로 응답할 경우, 순수 JSON 문자열만 추출합니다.
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
     * 코드 스타일 분석 (10가지 기준)
     */
    private String analyzeCodeStyle(String code) {
        try {
            String prompt = promptGenerator.createStyleAnalysisPrompt(code);
            String response = chatLanguageModel.generate(prompt);
            return cleanMarkdownCodeBlock(response);
        } catch (Exception e) {
            log.error("코드 스타일 분석 실패", e);
            return null; // 실패 시 null 반환 (메인 로직은 계속 진행)
        }
    }

    /**
     * 메타데이터 추출용 AI 호출
     */
    private String extractMetadata(String prompt) {
        try {
            String response = chatLanguageModel.generate(prompt);
            return cleanMarkdownCodeBlock(response);
        } catch (Exception e) {
            log.error("메타데이터 추출 실패", e);
            return "{}";
        }
    }

    /**
     * MCP 등 외부에서 요청된 Raw Code 분석
     * DB에 저장된 파일이 아니므로, 즉석에서 분석하고 결과만 반환합니다.
     * (선택적으로 기록을 남길 수도 있습니다)
     */
    @LangfuseObserve(name = "analyzeRawCode")
    public String analyzeRawCode(String code, String language, Long userId) {
        // 워크플로우 시작: MCP-Analysis Trace 생성
        langfuseService.startNamedTrace("MCP-Analysis",
                String.valueOf(userId),
                Map.of("mode", "MCP", "language", language));

        try {
            log.info("Raw Code Analysis Requested - User: {}, Language: {}, Length: {}", userId, language,
                    code.length());

            // 1. 사용자 컨텍스트 조회 (Hybrid Search)
            List<org.springframework.ai.document.Document> contextDocs = hybridSearchService.search(
                    "mistakes patterns errors improvement",
                    code,
                    3,
                    language);

            String userContext = contextDocs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));

            if (userContext.isEmpty()) {
                userContext = "No prior history available.";
            }

            // 2. 프롬프트 생성
            // Tone, Requirements는 기본값 사용
            String systemPromptWithTone = promptGenerator.createSystemPrompt(
                    List.of("Code Review", "Bug Detection"), // Default analysis types
                    1, // Default Tone (Level 1: The Tired Mentor)
                    "Focus on logic and security",
                    userContext,
                    null);

            // 3. Agentic Workflow 실행
            String aiResponseContent = agenticWorkflowService.executeWorkflow(code, systemPromptWithTone);

            String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);

            // 4. (Optional) Tracking mistakes even for raw code?
            // Yes, let's track it so the "Angry Teacher" learns from MCP usage too!
            try {
                JsonNode smellNode = objectMapper.readTree(cleanedResponse).path("codeSmells");
                updateUserPatterns(userId, smellNode);
                mistakeService.trackMistakes(userId, smellNode);
            } catch (Exception e) {
                log.warn("Failed to track mistakes for raw code analysis", e);
            }

            return cleanedResponse;

        } catch (Exception e) {
            log.error("Raw Code Analysis Failed", e);
            throw new RuntimeException("Code Analysis failed: " + e.getMessage());
        }
    }

    /**
     * 파일 확장자로 언어 추정
     */
    private String getLanguageFromExtension(String fileName) {
        if (fileName == null)
            return "java";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".py"))
            return "python";
        if (lower.endsWith(".cs"))
            return "csharp";
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx"))
            return "javascript";
        return "java";
    }

    /**
     * 분석 결과만 저장 (프론트엔드에서 분석 완료 후 호출용)
     *
     * @param analysisData 프론트엔드에서 전달받은 분석 결과 데이터
     * @param userId       현재 사용자 ID
     * @return 생성된 분석 ID
     */
    public String saveAnalysisOnly(java.util.Map<String, Object> analysisData, Long userId) {
        try {
            // 필수 데이터 추출
            String fileId = (String) analysisData.get("fileId");
            String repositoryUrl = (String) analysisData.get("repositoryUrl");
            String filePath = (String) analysisData.get("filePath");
            Object analysisResultObj = analysisData.get("analysisResult");

            // analysisResult를 JSON 문자열로 변환
            String analysisResultJson;
            if (analysisResultObj instanceof String) {
                analysisResultJson = (String) analysisResultObj;
            } else {
                analysisResultJson = objectMapper.writeValueAsString(analysisResultObj);
            }

            // JSON 파싱하여 상세 정보 추출
            JsonNode jsonNode = objectMapper.readTree(analysisResultJson);

            // CodeResultDTO 생성
            CodeResultDTO result = new CodeResultDTO();
            result.setAnalysisId(UUID.randomUUID().toString());
            result.setUserId(userId);
            result.setRepositoryUrl(repositoryUrl);
            result.setFilePath(filePath);
            result.setAnalysisResult(analysisResultJson);

            // 점수 및 상세 항목 매핑
            result.setAiScore(jsonNode.path("aiScore").asInt(-1));
            result.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            result.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));

            // 타임스탬프 설정
            result.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

            // DB 저장
            analysisMapper.saveCodeResult(result);

            log.info("분석 결과 저장 완료 - analysisId: {}, userId: {}", result.getAnalysisId(), userId);

            // 사용자 코드 패턴 업데이트
            updateUserPatterns(userId, jsonNode.path("codeSmells"));

            return result.getAnalysisId();

        } catch (Exception e) {
            log.error("분석 결과 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("분석 결과 저장에 실패했습니다: " + e.getMessage());
        }

    }

    /**
     * 메타데이터 및 스타일 분석 백필 (Backfill / Re-analysis)
     * 
     * 저장된 모든 분석 기록을 순회하며:
     * 1. 원본 코드를 다시 가져옵니다.
     * 2. 새로운 '코드 스타일 분석(Coding DNA)'을 수행합니다.
     * 3. 메타데이터를 다시 추출합니다.
     * 4. DB의 분석 결과 JSON과 METADATA 컬럼을 업데이트합니다.
     * 5. Vector Store(Qdrant)에 최신 정보를 다시 Ingest합니다.
     * 
     * 주의: 데이터 양에 따라 시간이 오래 걸릴 수 있는 무거운 작업입니다.
     */
    public String backfillMetadata() {
        log.info("Starting Metadata Backfill Process...");
        List<CodeResultDTO> allResults = analysisMapper.findAllCodeResults();
        int successCount = 0;
        int failureCount = 0;

        for (CodeResultDTO result : allResults) {
            try {
                log.info("Backfilling Analysis ID: {}", result.getAnalysisId());

                // 1. 원본 파일 내용 조회 (저장 당시 스냅샷이 없으면 최신파일 조회 시도)
                // 현재 구조상 CodeResultDTO에는 파일 내용이 없음. GithubFileDTO를 찾아야 함.
                // 꼼수: result.getFilePath()와 RepoUrl로 GithubFiles를 찾거나,
                // 만약 Analysis ID가 GithubFile ID와 연동이 안되어 있다면(구조상),
                // 가장 정확한 건 당시 분석했던 '코드 내용'이 필요한데,
                // 현재 DB 스키마상 CODE_ANALYSIS_HISTORY에는 '코드 원본'이 저장되지 않음.
                // 따라서 GITHUB_FILES 테이블에서 해당 경로의 최신 파일을 가져와야 함 (근사치).

                // 더 정확한 방법: result.getRepositoryUrl() + result.getFilePath() 로 GITHUB_FILES 조회
                GithubFileDTO fileDto = analysisMapper.findLatestFileContent(result.getRepositoryUrl(),
                        result.getFilePath());

                if (fileDto == null) {
                    log.warn("Skipping {}: Original file not found in DB.", result.getAnalysisId());
                    failureCount++;
                    continue;
                }

                String codeContent = fileDto.getFileContent();

                // 2. 새로운 스타일 분석 실행
                String styleJson = analyzeCodeStyle(codeContent);

                // 3. 메타데이터 재추출
                String metadataPrompt = promptGenerator.createMetadataPrompt(codeContent);
                String metadataJson = extractMetadata(metadataPrompt);

                // 4. 기존 분석 결과 JSON 업데이트 (Code Style 병합)
                String originalJson = result.getAnalysisResult();
                String updatedJson = originalJson;

                if (styleJson != null) {
                    try {
                        JsonNode originalNode = objectMapper.readTree(originalJson);
                        if (originalNode instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                            JsonNode styleNode = objectMapper.readTree(styleJson);
                            ((com.fasterxml.jackson.databind.node.ObjectNode) originalNode).set("styleAnalysis",
                                    styleNode);
                            updatedJson = objectMapper.writeValueAsString(originalNode);
                        }
                    } catch (Exception e) {
                        log.error("Failed to merge style JSON for backfill", e);
                    }
                }

                // 5. DTO 업데이트 및 DB 저장
                result.setAnalysisResult(updatedJson);
                result.setMetadata(metadataJson);
                // AI 점수나 제안 등은 그대로 유지하거나 파싱해서 업데이트할 수도 있음 (여기선 생략)

                analysisMapper.updateCodeResult(result);

                // 6. Vector Store Re-ingest
                try {
                    RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                            String.valueOf(result.getUserId()),
                            metadataJson,
                            updatedJson,
                            result.getFilePath().substring(result.getFilePath().lastIndexOf(".") + 1),
                            result.getFilePath(),
                            "Backfeed Re-analysis",
                            result.getCustomRequirements(),
                            result.getAnalysisId());
                    ragService.ingestCode(ingestRequest);
                } catch (Exception e) {
                    log.error("Failed to re-ingest to Vector Store", e);
                    // DB 저장은 성공했으므로 카운트는 성공으로 간주하거나 별도 처리
                }

                successCount++;

            } catch (Exception e) {
                log.error("Failed to backfill Analysis ID: " + result.getAnalysisId(), e);
                failureCount++;
            }
        }

        return String.format("Backfill Complete. Success: %d, Failure: %d", successCount, failureCount);
    }
}