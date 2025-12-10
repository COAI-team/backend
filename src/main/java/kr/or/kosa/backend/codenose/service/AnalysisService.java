package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.config.PromptManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final ChatClient chatClient;
    private final RagService ragService;
    private final HybridSearchService hybridSearchService;
    private final AgenticWorkflowService agenticWorkflowService;

    // 설정 및 프롬프트 관리
    // 설정 및 프롬프트 관리
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
        this.promptGenerator = promptGenerator;
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
            // 유사한 패턴이나 실수를 검색하여, AI가 "아, 이 사용자는 이런 실수를 자주 했었지"라고 인지하게 함
            String language = getLanguageFromExtension(storedFile.getFileName());
            List<org.springframework.ai.document.Document> contextDocs = hybridSearchService.search(
                    "mistakes patterns errors improvement",
                    storedFile.getFileContent(),
                    3,
                    language);

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
            String systemPromptWithTone = promptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements(),
                    userContext);

            // 3.1. 메타데이터 별도 추출 (파일의 기술적 특성 파악용)
            String metadataPrompt = promptGenerator.createMetadataPrompt(storedFile.getFileContent());
            String metadataJson = extractMetadata(metadataPrompt);
            log.info("메타데이터 추출 상태: {}", metadataJson != null ? "성공" : "실패");

            // 4. Agentic Workflow 실행
            // 단순 1회성 호출이 아니라, 에이전트가 생각하고 판단하는 워크플로우를 태웁니다.
            String aiResponseContent = agenticWorkflowService.executeWorkflow(storedFile.getFileContent(),
                    systemPromptWithTone);

            String cleanedResponse = cleanMarkdownCodeBlock(aiResponseContent);

            // 5. 분석 결과를 CODE_ANALYSIS_HISTORY 테이블에 저장
            String analysisId = saveAnalysisResult(storedFile, requestDto, cleanedResponse, metadataJson,
                    relatedAnalysisIdsJson);

            // 6. 사용자 코드 패턴 업데이트 (WordCloud나 통계에 활용됨)
            updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(cleanedResponse).path("codeSmells"));

            log.info("AI 분석 완료 - analysisId: {}, fileId: {}, toneLevel: {}",
                    analysisId, storedFile.getFileId(), requestDto.getToneLevel());

            // 7. RAG VectorDB에 저장 (Ingest)
            // 이번 분석 결과를 지식베이스에 추가하여, 다음 분석 때 참조할 수 있게 합니다.
            try {
                RagDto.IngestRequest ingestRequest = new RagDto.IngestRequest(
                        String.valueOf(requestDto.getUserId()),
                        metadataJson, // 메타데이터를 컨텐츠로 활용
                        cleanedResponse,
                        storedFile.getFileName().substring(storedFile.getFileName().lastIndexOf(".") + 1), // 확장자 추출
                        storedFile.getFilePath(),
                        "Stored File Metadata",
                        requestDto.getCustomRequirements(),
                        analysisId); // 분석 ID 연결
                ragService.ingestCode(ingestRequest);
            } catch (Exception e) {
                log.error("RAG 시스템에 분석 결과 저장 실패", e);
            }

            return cleanedResponse;

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
     * 메타데이터 추출용 AI 호출
     */
    private String extractMetadata(String prompt) {
        try {
            String response = chatClient.prompt(prompt).call().content();
            return cleanMarkdownCodeBlock(response);
        } catch (Exception e) {
            log.error("메타데이터 추출 실패", e);
            return "{}";
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
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx"))
            return "javascript";
        return "java";
    }
}