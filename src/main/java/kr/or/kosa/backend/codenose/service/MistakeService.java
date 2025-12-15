package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.UserMistakeStatDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import kr.or.kosa.backend.codenose.service.LangfuseService;
import kr.or.kosa.backend.codenose.service.trace.LangfuseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자 실수(Mistake) 관리 및 리포트/퀴즈 생성 서비스
 */
@Slf4j
@Service
public class MistakeService {

    private final AnalysisMapper analysisMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptGenerator promptGenerator;
    private final LangfuseService langfuseService;

    public MistakeService(AnalysisMapper analysisMapper, ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper, PromptGenerator promptGenerator, LangfuseService langfuseService) {
        this.analysisMapper = analysisMapper;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.promptGenerator = promptGenerator;
        this.langfuseService = langfuseService;
    }

    // 30회 이상 반복 시 경고 트리거
    private static final int MISTAKE_THRESHOLD = 2;

    /**
     * 분석 결과에서 발견된 에러를 카운팅 (AnalysisService에서 호출)
     */
    public void trackMistakes(Long userId, JsonNode codeSmells) {
        if (codeSmells == null || !codeSmells.isArray())
            return;

        for (JsonNode smell : codeSmells) {
            String type = smell.path("name").asText();
            if (type.isEmpty() || type.equals("Analysis Summary"))
                continue;

            UserMistakeStatDTO stat = analysisMapper.findMistakeStat(userId, type);
            if (stat == null) {
                stat = UserMistakeStatDTO.builder()
                        .userId(userId)
                        .mistakeType(type)
                        .occurrenceCount(1)
                        .solvedCount(0)
                        .lastDetectedAt(new Timestamp(System.currentTimeMillis()))
                        .build();
            } else {
                stat.setOccurrenceCount(stat.getOccurrenceCount() + 1);
                stat.setLastDetectedAt(new Timestamp(System.currentTimeMillis()));
            }
            analysisMapper.saveOrUpdateMistakeStat(stat);
        }
    }

    /**
     * 사용자가 경고 배너를 봐야 하는지 확인
     */
    public boolean checkAlertCondition(Long userId) {
        List<UserMistakeStatDTO> stats = analysisMapper.findMistakeStatsByUserId(userId);
        log.info("Checking alert for userId: {}, Found {} stats", userId, stats.size());

        for (UserMistakeStatDTO stat : stats) {
            int netCount = stat.getOccurrenceCount() - (stat.getSolvedCount() * MISTAKE_THRESHOLD);
            log.info("Mistake: {}, Occurr: {}, Solved: {}, Net: {}, Threshold: {}",
                    stat.getMistakeType(), stat.getOccurrenceCount(), stat.getSolvedCount(), netCount,
                    MISTAKE_THRESHOLD);

            if (netCount >= MISTAKE_THRESHOLD) {
                log.info("Alert Triggered for user {} due to mistake {}", userId, stat.getMistakeType());
                return true;
            }
        }
        return false;
    }

    /**
     * AI를 통한 상세 리포트 및 O/X 퀴즈 생성
     */
    public String generateMistakeReport(Long userId) {
        // 트레이스 시작
        String traceId = UUID.randomUUID().toString();
        langfuseService.startTrace(traceId, "generateMistakeReport", String.valueOf(userId), Collections.emptyMap());

        try {
            // 1. Threshold 넘긴 실수 상위 3개 조회
            List<UserMistakeStatDTO> criticalMistakes = new ArrayList<>();
            List<UserMistakeStatDTO> allStats = analysisMapper.findMistakeStatsByUserId(userId);

            for (UserMistakeStatDTO stat : allStats) {
                int netCount = stat.getOccurrenceCount() - (stat.getSolvedCount() * MISTAKE_THRESHOLD);
                if (netCount >= MISTAKE_THRESHOLD) {
                    criticalMistakes.add(stat);
                }
            }

            if (criticalMistakes.isEmpty()) {
                // 임계치 넘지 않아도, 발생 빈도 높은 순으로 3개 제공 (Quiz 모드 아님)
                criticalMistakes = new ArrayList<>(allStats);
                criticalMistakes.sort((a, b) -> b.getOccurrenceCount() - a.getOccurrenceCount());
            }

            if (criticalMistakes.size() > 3) {
                criticalMistakes = criticalMistakes.subList(0, 3);
            }

            StringBuilder mistakesContext = new StringBuilder();
            for (UserMistakeStatDTO stat : criticalMistakes) {
                mistakesContext.append("- Mistake Type: ").append(stat.getMistakeType())
                        .append(" (Repeated ").append(stat.getOccurrenceCount()).append(" times)\n");
            }

            // 2. AI 호출 (MISTAKE_REPORT_PROMPT 사용)
            String prompt = promptGenerator.createMistakeReportPrompt(mistakesContext.toString());

            // 수동 스팬 시작 (Spring AI ChatClient 사용 - LangChain4j 리스너가 커버하지 않음)
            Instant start = Instant.now();
            langfuseService.startSpan("MistakeReportGeneration", start, Collections.emptyMap());

            String response = chatClient.prompt(prompt).call().content();

            // Spring AI 사용이므로 수동으로 Generation 로그 기록
            langfuseService.sendGeneration(
                    "MistakeReport",
                    start,
                    Instant.now(),
                    "gpt-4o",
                    prompt,
                    response,
                    0);

            langfuseService.endSpan(null, Instant.now(), Collections.emptyMap());

            return response;

        } catch (Exception e) {
            log.error("Mistake Report Generation Failed", e);
            throw new RuntimeException("리포트 생성 실패", e);
        } finally {
            LangfuseContext.clean();
        }
    }

    /**
     * 퀴즈 통과 처리 (30회 차감 효과)
     */
    public void solveMistake(Long userId, String mistakeType) {
        UserMistakeStatDTO stat = analysisMapper.findMistakeStat(userId, mistakeType);
        if (stat != null) {
            stat.setSolvedCount(stat.getSolvedCount() + 1);
            analysisMapper.saveOrUpdateMistakeStat(stat);
        }
    }

    /**
     * 퀴즈 통과 시, 현재 임계치를 넘은 모든 실수에 대해 해결 처리
     */
    public void solveAllCriticalMistakes(Long userId) {
        List<UserMistakeStatDTO> allStats = analysisMapper.findMistakeStatsByUserId(userId);
        for (UserMistakeStatDTO stat : allStats) {
            int netCount = stat.getOccurrenceCount() - (stat.getSolvedCount() * MISTAKE_THRESHOLD);
            if (netCount >= MISTAKE_THRESHOLD) {
                stat.setSolvedCount(stat.getSolvedCount() + 1);
                analysisMapper.saveOrUpdateMistakeStat(stat);
            }
        }
    }

    /**
     * 특정 실수(Mistake Type)가 발생한 코드 내역 상세 조회
     * mistakeType이 null이면, 현재 Alert 조건을 만족하는(임계치 초과) 모든 실수에 대한 상세 내역을 반환합니다.
     */
    public List<Map<String, Object>> getMistakeDetails(Long userId, String mistakeType) {
        List<kr.or.kosa.backend.codenose.dto.CodeResultDTO> history = analysisMapper.findCodeResultByUserId(userId);
        List<Map<String, Object>> details = new ArrayList<>();

        List<String> targetMistakes = new ArrayList<>();
        if (mistakeType != null && !mistakeType.isEmpty()) {
            targetMistakes.add(mistakeType);
        } else {
            // 1. Identify Target Mistakes (Logic aligned with generateMistakeReport)
            List<UserMistakeStatDTO> criticalMistakes = new ArrayList<>();
            List<UserMistakeStatDTO> allStats = analysisMapper.findMistakeStatsByUserId(userId);

            for (UserMistakeStatDTO stat : allStats) {
                int netCount = stat.getOccurrenceCount() - (stat.getSolvedCount() * MISTAKE_THRESHOLD);
                if (netCount >= MISTAKE_THRESHOLD) {
                    criticalMistakes.add(stat);
                }
            }

            // Fallback: If no critical mistakes, use all stats (to show something in
            // demo/report)
            if (criticalMistakes.isEmpty()) {
                criticalMistakes = new ArrayList<>(allStats);
            }

            // Sort by Occurrence Count Descending (to find "Top" mistakes)
            criticalMistakes.sort((a, b) -> Integer.compare(b.getOccurrenceCount(), a.getOccurrenceCount()));

            // Limit to Top 3
            if (criticalMistakes.size() > 3) {
                criticalMistakes = criticalMistakes.subList(0, 3);
            }

            for (UserMistakeStatDTO stat : criticalMistakes) {
                targetMistakes.add(stat.getMistakeType());
            }
        }

        if (targetMistakes.isEmpty()) {
            return details;
        }

        for (kr.or.kosa.backend.codenose.dto.CodeResultDTO result : history) {
            try {
                JsonNode smells = objectMapper.readTree(result.getCodeSmells());
                if (smells.isArray()) {
                    for (JsonNode smell : smells) {
                        String currentSmellName = smell.path("name").asText();
                        if (targetMistakes.contains(currentSmellName)) {
                            Map<String, Object> detail = new HashMap<>(); // Simplified
                            detail.put("analysisId", result.getAnalysisId());
                            detail.put("filePath", result.getFilePath());
                            detail.put("createdAt", result.getCreatedAt());
                            detail.put("repositoryUrl", result.getRepositoryUrl());
                            detail.put("mistakeType", currentSmellName); // 실수 유형 추가

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
                log.error("실수 상세 파싱 오류", e);
            }
        }
        return details;
    }
}
