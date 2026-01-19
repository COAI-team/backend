package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 워드 클라우드 서비스 (WordCloudService)
 * 
 * 변경사항:
 * Kumo 라이브러리 대신 프론트엔드 ECharts WordCloud에서 사용할
 * JSON 데이터(빈도수 맵)를 반환하도록 수정됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    /**
     * 월별 워드 클라우드 데이터 생성 (JSON)
     * 
     * ECharts wordCloud 확장에서 바로 사용할 수 있는
     * [{name: "키워드", value: 빈도수}, ...] 형태로 반환합니다.
     * 
     * @param userId 사용자 ID
     * @param year   조회할 연도
     * @param month  조회할 월
     * @return 워드 클라우드용 데이터 리스트 (데이터가 없으면 빈 리스트)
     */
    public List<Map<String, Object>> generateWordCloudData(Long userId, int year, int month) {
        try {
            // 1. 해당 월의 데이터 조회 기간 설정
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDateTime startDateTime = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime endDateTime = yearMonth.atEndOfMonth().atTime(23, 59, 59);

            List<CodeResultDTO> results = analysisMapper.findCodeResultsByUserIdAndDateRange(
                    userId,
                    Timestamp.valueOf(startDateTime),
                    Timestamp.valueOf(endDateTime));

            if (results.isEmpty()) {
                return Collections.emptyList();
            }

            // 2. 빈도수 집계 (Aggregation)
            Map<String, Integer> frequencyMap = new HashMap<>();
            for (CodeResultDTO result : results) {
                String codeSmellsJson = result.getCodeSmells();
                if (codeSmellsJson != null && !codeSmellsJson.isEmpty()) {
                    try {
                        JsonNode root = objectMapper.readTree(codeSmellsJson);
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                String name = node.path("name").asText();
                                // "Analysis Summary"는 제외
                                if (!name.isEmpty() && !"Analysis Summary".equals(name)) {
                                    frequencyMap.put(name, frequencyMap.getOrDefault(name, 0) + 1);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Code Smell JSON 파싱 실패 analysisId: {}", result.getAnalysisId());
                    }
                }
            }

            if (frequencyMap.isEmpty()) {
                return Collections.emptyList();
            }

            // 3. ECharts 형식으로 변환: [{name: "xxx", value: n}, ...]
            return frequencyMap.entrySet().stream()
                    .map(entry -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", entry.getKey());
                        item.put("value", entry.getValue());
                        return item;
                    })
                    .sorted((a, b) -> ((Integer) b.get("value")).compareTo((Integer) a.get("value")))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("워드 클라우드 데이터 생성 중 오류 발생", e);
            throw new RuntimeException("워드 클라우드 데이터 생성 실패", e);
        }
    }
}
