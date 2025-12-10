package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 인사이트 서비스 (InsightsService)
 * 
 * 역할:
 * 사용자에게 분석된 코드를 바탕으로 유의미한 통계와 트렌드를 제공합니다.
 * 월별 Code Smell 발생 추이, 자주 발생하는 패턴 분석, 그리고 상세 코드 스니펫 조회 등을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final AnalysisMapper analysisMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 사용자별 모든 코드 분석 이력 조회
     * 
     * @param userId 사용자 ID
     * @return 코드 분석 결과 리스트 (전체 이력)
     */
    public List<CodeResultDTO> getCodeResult(Long userId) {
        log.info("코드 분석 결과 전체 조회 - userId: {}", userId);
        return analysisMapper.findCodeResultByUserId(userId);
    }

    /**
     * 사용자별 자주 발생하는 코드 패턴(버릇) 조회
     * 
     * @param userId 사용자 ID
     * @return 패턴 리스트 (빈도수 포함)
     */
    public List<UserCodePatternDTO> getUserCodePatterns(Long userId) {
        log.info("사용자 코드 패턴(버릇) 조회 - userId: {}", userId);
        return analysisMapper.findAllPatternsByUserId(userId);
    }

    /**
     * 사용자별 패턴 발생 트렌드 분석 (월별 추이)
     * 
     * 분석된 모든 기록(`CODE_ANALYSIS_HISTORY`)을 가져와서,
     * JSON으로 저장된 `codeSmells`를 파싱하여 월별로 어떤 패턴이 얼마나 발생했는지 집계합니다.
     * 프론트엔드 차트(Recharts 등)에서 사용하기 좋은 형태로 변환하여 반환합니다.
     * 
     * @param userId 사용자 ID
     * @return 월별 패턴 발생 횟수 맵 (Map<Month, PatternCounts>)
     */
    public java.util.Map<String, Object> getPatternTrends(Long userId) {
        List<CodeResultDTO> history = analysisMapper.findCodeResultByUserId(userId);

        // 집계용 자료구조: Month -> (PatternName -> Count)
        // TreeMap을 사용하여 월별 정렬 (예: 2024-01, 2024-02 ...)
        java.util.Map<String, java.util.Map<String, Integer>> monthlyData = new java.util.TreeMap<>();

        for (CodeResultDTO result : history) {
            try {
                // YYYY-MM 형식으로 월 추출
                String month = result.getCreatedAt().toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

                // DB에 저장된 JSON 문자열 파싱
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
                log.error("Code Smell 파싱 오류 analysisId: {}", result.getAnalysisId(), e);
            }
        }

        // 프론트엔드용 데이터 변환
        // 예: [{ month: "2023-10", "NullPointerException": 5, "Hardcoded String": 2,
        // total: 7 }]
        java.util.List<java.util.Map<String, Object>> chartData = new java.util.ArrayList<>();
        java.util.Set<String> allPatterns = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> entry : monthlyData.entrySet()) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("month", entry.getKey());

            int total = 0;
            for (java.util.Map.Entry<String, Integer> patternEntry : entry.getValue().entrySet()) {
                row.put(patternEntry.getKey(), patternEntry.getValue());
                allPatterns.add(patternEntry.getKey()); // 범례(Legend)용 전체 패턴 집합
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
     * 특정 패턴 상세 조회 (실제 코드 스니펫 포함)
     * 
     * 사용자가 특정 패턴(예: "Unused Variable")을 클릭했을 때,
     * 해당 패턴이 발생했던 과거의 실제 코드 조각과 설명을 모아서 보여줍니다.
     */
    public List<java.util.Map<String, Object>> getPatternDetails(Long userId, String patternName) {
        List<CodeResultDTO> history = analysisMapper.findCodeResultByUserId(userId);
        List<java.util.Map<String, Object>> details = new java.util.ArrayList<>();

        for (CodeResultDTO result : history) {
            try {
                com.fasterxml.jackson.databind.JsonNode smells = objectMapper.readTree(result.getCodeSmells());
                if (smells.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode smell : smells) {
                        // 요청된 패턴명과 일치하는 항목만 추출
                        if (patternName.equals(smell.path("name").asText())) {
                            java.util.Map<String, Object> detail = new java.util.HashMap<>();
                            detail.put("analysisId", result.getAnalysisId());
                            detail.put("filePath", result.getFilePath());
                            detail.put("createdAt", result.getCreatedAt());
                            detail.put("repositoryUrl", result.getRepositoryUrl());

                            // 코드 스니펫 추출 ('code' 필드 우선, 없으면 'problematicCode')
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
                log.error("패턴 상세 파싱 오류: {}", patternName, e);
            }
        }
        return details;
    }
}