package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final AnalysisMapper analysisMapper;

    /**
     * 사용자별 코드 분석 결과 조회
     * 
     * @param userId 사용자 ID
     * @return 코드 분석 결과 리스트
     */
    public List<CodeResultDTO> getCodeResult(Long userId) {
        System.out.println("[TRACE] InsightsService.getCodeResult called with userId: " + userId);
        log.info("코드 분석 결과 조회 - userId: {}", userId);
        return analysisMapper.findCodeResultByUserId(userId);
    }

    /**
     * 사용자별 코드 패턴 조회
     * 
     * @param userId 사용자 ID
     * @return 사용자 코드 패턴 리스트
     */
    public List<UserCodePatternDTO> getUserCodePatterns(Long userId) {
        System.out.println("[TRACE] InsightsService.getUserCodePatterns called with userId: " + userId);
        log.info("사용자 코드 패턴 조회 - userId: {}", userId);
        return analysisMapper.findAllPatternsByUserId(userId);
    }

    /**
     * 특정 분석 결과 상세 조회
     * 
     * @param analysisId 분석 ID
     * @return 코드 분석 결과
     */
    public CodeResultDTO getAnalysisDetail(String analysisId) {
        System.out.println("[TRACE] InsightsService.getAnalysisDetail called with analysisId: " + analysisId);
        log.info("분석 결과 상세 조회 - analysisId: {}", analysisId);
        return analysisMapper.findCodeResultById(analysisId);
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 사용자별 패턴 트렌드 분석 (월별 발생 빈도)
     */
    public java.util.Map<String, Object> getPatternTrends(Long userId) {
        System.out.println("[TRACE] InsightsService.getPatternTrends called with userId: " + userId);
        List<CodeResultDTO> history = analysisMapper.findCodeResultByUserId(userId);

        // Month -> Pattern -> Count
        java.util.Map<String, java.util.Map<String, Integer>> monthlyData = new java.util.TreeMap<>();

        for (CodeResultDTO result : history) {
            try {
                String month = result.getCreatedAt().toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                com.fasterxml.jackson.databind.JsonNode smells = objectMapper.readTree(result.getCodeSmells());

                if (smells.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode smell : smells) {
                        String patternName = smell.path("name").asText();
                        if (patternName == null || patternName.isEmpty())
                            continue;

                        monthlyData.putIfAbsent(month, new java.util.HashMap<>());
                        java.util.Map<String, Integer> patternCounts = monthlyData.get(month);
                        patternCounts.put(patternName, patternCounts.getOrDefault(patternName, 0) + 1);
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing code smells for analysisId: {}", result.getAnalysisId(), e);
            }
        }

        // Transform for Frontend: [{ month: "2023-10", patternA: 5, patternB: 2, total:
        // 7 }]
        java.util.List<java.util.Map<String, Object>> chartData = new java.util.ArrayList<>();
        java.util.Set<String> allPatterns = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> entry : monthlyData.entrySet()) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("month", entry.getKey());

            int total = 0;
            for (java.util.Map.Entry<String, Integer> patternEntry : entry.getValue().entrySet()) {
                row.put(patternEntry.getKey(), patternEntry.getValue());
                allPatterns.add(patternEntry.getKey());
                total += patternEntry.getValue();
            }
            row.put("total", total);
            chartData.add(row);
        }

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("data", chartData);
        response.put("patterns", allPatterns);

        return response;
    }

    /**
     * 특정 패턴 상세 조회 (발생한 코드 위치 및 스니펫 포함)
     */
    public List<java.util.Map<String, Object>> getPatternDetails(Long userId, String patternName) {
        System.out.println("[TRACE] InsightsService.getPatternDetails called with userId: " + userId + ", patternName: "
                + patternName);
        List<CodeResultDTO> history = analysisMapper.findCodeResultByUserId(userId);
        List<java.util.Map<String, Object>> details = new java.util.ArrayList<>();

        for (CodeResultDTO result : history) {
            try {
                com.fasterxml.jackson.databind.JsonNode smells = objectMapper.readTree(result.getCodeSmells());
                if (smells.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode smell : smells) {
                        if (patternName.equals(smell.path("name").asText())) {
                            java.util.Map<String, Object> detail = new java.util.HashMap<>();
                            detail.put("analysisId", result.getAnalysisId());
                            detail.put("filePath", result.getFilePath());
                            detail.put("createdAt", result.getCreatedAt());
                            detail.put("repositoryUrl", result.getRepositoryUrl());

                            // Extract snippet if available (assuming 'code' or 'problematicCode' field)
                            String snippet = smell.path("code").asText(null);
                            if (snippet == null)
                                snippet = smell.path("problematicCode").asText("");

                            detail.put("code", snippet);
                            detail.put("description", smell.path("description").asText());
                            detail.put("severity", smell.path("severity").asText("UNKNOWN"));

                            details.add(detail);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing details for pattern: {}", patternName, e);
            }
        }
        return details;
    }

    /**
     * 사용자의 특정 패턴 조회
     * 
     * @param userId      사용자 ID
     * @param patternType 패턴 타입
     * @return 사용자 코드 패턴
     */
    public UserCodePatternDTO getSpecificPattern(Long userId, String patternType) {
        System.out.println("[TRACE] InsightsService.getSpecificPattern called with userId: " + userId
                + ", patternType: " + patternType);
        log.info("특정 패턴 조회 - userId: {}, patternType: {}", userId, patternType);
        return analysisMapper.findUserCodePattern(userId, patternType);
    }
}