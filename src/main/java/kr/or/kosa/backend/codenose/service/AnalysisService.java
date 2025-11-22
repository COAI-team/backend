package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.dtoReal.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.chat.ChatClient;
// import org.springframework.ai.chat.ChatResponse;
// import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;


import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    // private final ChatClient chatClient; // Commented out due to build issues
    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;


    private final ChatClient chatClient;

    @Autowired
    public AnalysisService(
            ChatClient.Builder chatClientBuilder,
            AnalysisMapper analysisMapper,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClientBuilder.build();
        this.analysisMapper = analysisMapper;
        this.objectMapper = objectMapper;
    }

    private final String systemPrompt = """
            """;

    // @Transactional // Commented out as transactional logic might not apply to dummy response
    public String analyzeCode(String userId, String userMessage, AnalysisRequestDTO requestDto) {
        try {


            List<Message> conversion = new ArrayList<Message>();
            conversion.add(new SystemPromptTemplate(systemPrompt).createMessage());
            conversion.add(new UserMessage(userMessage));


            Prompt prompt = new Prompt(conversion);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

            // 1. Generate Prompt (optional, as we are using a dummy response)
            // Prompt prompt = PromptGenerator.createPrompt(requestDto);

            // 2. Simulate AI Model Call with Dummy Response
            String aiResponseContent =response.getResult()
                    .getOutput()
                    .getText();

            // 3. Save to Database
            saveAnalysis(requestDto, aiResponseContent);

            // 4. Return AI response
            return aiResponseContent;

        } catch (Exception e) {
            log.error("Error during code analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze code.", e);
        }
    }

    /**
     * 저장된 파일을 조회하여 AI 분석 수행 (2단계: AI 분석)
     * @param requestDto 분석 요청 DTO (repositoryUrl, filePath, analysisTypes, toneLevel, customRequirements 포함)
     * @return AI 분석 결과 (JSON 문자열)
     */
    public String analyzeStoredFile(AnalysisRequestDTO requestDto) {
        try {
            // 1. DB에서 저장된 파일 내용 조회
            CodeResultDTO storedFile = analysisMapper.findLatestFileContent(
                    requestDto.getRepositoryUrl(),
                    requestDto.getFilePath()
            );

            if (storedFile == null) {
                throw new RuntimeException("저장된 파일을 찾을 수 없습니다. repositoryUrl: "
                        + requestDto.getRepositoryUrl() + ", filePath: " + requestDto.getFilePath());
            }

            // 2. 프롬프트 생성 (toneLevel에 따른 시스템 프롬프트)
            String systemPromptWithTone = PromptGenerator.createSystemPrompt(
                    requestDto.getAnalysisTypes(),
                    requestDto.getToneLevel(),
                    requestDto.getCustomRequirements()
            );

            // 3. AI 메시지 구성
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemPromptTemplate(systemPromptWithTone).createMessage());
            messages.add(new UserMessage("다음 코드를 분석해주세요:\n\n" + requestDto.getCode()));

            // 4. AI 호출
            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String aiResponseContent = response.getResult().getOutput().getText();

            // 5. 분석 결과를 DB에 업데이트
            updateAnalysisResult(storedFile.getAnalysisId(), requestDto, aiResponseContent);

            // 6. 사용자 코드 패턴 업데이트
            updateUserPatterns(requestDto.getUserId(), objectMapper.readTree(aiResponseContent).path("codeSmells"));

            log.info("AI 분석 완료 - analysisId: {}, toneLevel: {}", storedFile.getAnalysisId(), requestDto.getToneLevel());

            return aiResponseContent;

        } catch (Exception e) {
            log.error("파일 분석 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 분석에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * 분석 결과를 DB에 업데이트
     */
    private void updateAnalysisResult(String analysisId, AnalysisRequestDTO requestDto, String aiResponseContent) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResponseContent);

            CodeResultDTO result = new CodeResultDTO();
            result.setAnalysisId(analysisId);
            result.setAnalysisType(String.join(", ", requestDto.getAnalysisTypes()));
            result.setToneLevel(requestDto.getToneLevel());
            result.setCustomRequirements(requestDto.getCustomRequirements());
            result.setAnalysisResult(aiResponseContent);
            result.setAiScore(jsonNode.path("aiScore").asInt(-1));
            result.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            result.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));

            analysisMapper.updateAnalysisResult(result);

        } catch (Exception e) {
            log.error("분석 결과 업데이트 실패: {}", e.getMessage(), e);
            throw new RuntimeException("분석 결과 저장에 실패했습니다.", e);
        }
    }

    private void saveAnalysis(AnalysisRequestDTO requestDto, String aiResponseContent) {
        try {
            var jsonNode = objectMapper.readTree(aiResponseContent);

            CodeResultDTO history = new CodeResultDTO();
            history.setAnalysisId(UUID.randomUUID().toString());
            history.setUserId(requestDto.getUserId());
            history.setRepositoryUrl(requestDto.getRepositoryUrl());
            history.setFilePath(requestDto.getFilePath());
            history.setAnalysisType(String.join(", ", requestDto.getAnalysisTypes()));
            history.setToneLevel(requestDto.getToneLevel());
            history.setCustomRequirements(requestDto.getCustomRequirements());
            history.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            history.setAnalysisResult(aiResponseContent);
            history.setAiScore(jsonNode.path("aiScore").asInt(-1));
            history.setCodeSmells(objectMapper.writeValueAsString(jsonNode.path("codeSmells")));
            history.setSuggestions(objectMapper.writeValueAsString(jsonNode.path("suggestions")));

            analysisMapper.saveCodeResult(history);
            
            // Update user code patterns based on the result
            updateUserPatterns(requestDto.getUserId(), jsonNode.path("codeSmells"));

        } catch (Exception e) {
            log.error("Failed to save analysis history and patterns: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save analysis results.", e);
        }
    }

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
}
