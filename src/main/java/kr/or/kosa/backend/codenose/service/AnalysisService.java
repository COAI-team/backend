package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.AnalysisRequestDto;
import kr.or.kosa.backend.codenose.dto.CodeAnalysisHistoryDto;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDto;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.chat.ChatClient;
// import org.springframework.ai.chat.ChatResponse;
// import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;


import java.sql.Timestamp;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    // private final ChatClient chatClient; // Commented out due to build issues
    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    // @Transactional // Commented out as transactional logic might not apply to dummy response
    public String analyzeCode(AnalysisRequestDto requestDto) {
        try {
            // 1. Generate Prompt (optional, as we are using a dummy response)
            // Prompt prompt = PromptGenerator.createPrompt(requestDto);

            // 2. Simulate AI Model Call with Dummy Response
            String aiResponseContent = """
                {
                  "aiScore": 75,
                  "codeSmells": [
                    {
                      "name": "Long Method",
                      "description": "The 'analyzeCode' method is too long and performs multiple responsibilities. Consider refactoring."
                    },
                    {
                      "name": "Magic Number",
                      "description": "The hardcoded 'userId: 1' in the frontend is a magic number. It should be dynamically fetched from the authenticated user context."
                    }
                  ],
                  "suggestions": [
                    {
                      "problematicSnippet": "public String analyzeCode(AnalysisRequestDto requestDto) { ... }",
                      "proposedReplacement": "Refactor 'analyzeCode' into smaller, more focused methods, e.g., 'generateAiPrompt', 'callAiModel', 'saveAnalysisResults'."
                    },
                    {
                      "problematicSnippet": "userId: 1",
                      "proposedReplacement": "Replace 'userId: 1' with a value obtained from the authenticated user's session or token."
                    }
                  ]
                }
                """;

            // 3. Save to Database
            saveAnalysis(requestDto, aiResponseContent);

            // 4. Return AI response
            return aiResponseContent;

        } catch (Exception e) {
            log.error("Error during code analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze code.", e);
        }
    }

    private void saveAnalysis(AnalysisRequestDto requestDto, String aiResponseContent) {
        try {
            var jsonNode = objectMapper.readTree(aiResponseContent);

            CodeAnalysisHistoryDto history = new CodeAnalysisHistoryDto();
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

            analysisMapper.saveAnalysisHistory(history);
            
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

            UserCodePatternDto existingPattern = analysisMapper.findUserCodePattern(userId, patternType);

            if (existingPattern != null) {
                existingPattern.setFrequency(existingPattern.getFrequency() + 1);
                existingPattern.setLastDetected(new Timestamp(System.currentTimeMillis()));
                analysisMapper.updateUserCodePattern(existingPattern);
            } else {
                UserCodePatternDto newPattern = new UserCodePatternDto();
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
