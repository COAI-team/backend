package kr.or.kosa.backend.algorithm.service.external;

import kr.or.kosa.backend.algorithm.dto.external.LeetCodeProblemDto;
import kr.or.kosa.backend.algorithm.dto.external.ProblemDocumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LeetCode 문제 크롤러
 * alfa-leetcode-api를 사용하여 문제 상세 정보 수집
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeetCodeCrawler {

    private static final String BASE_URL = "https://alfa-leetcode-api.onrender.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String LEETCODE_URL = "https://leetcode.com/problems/";

    private final WebClient webClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .build();

    /**
     * LeetCode 문제 상세 정보 크롤링
     *
     * @param basicProblem 기본 문제 정보 (목록 API에서 가져온 것)
     * @return Vector DB 저장용 문제 문서
     */
    public ProblemDocumentDto crawlProblemDetail(LeetCodeProblemDto basicProblem) {
        String titleSlug = basicProblem.getTitleSlug();
        log.info("🔍 LeetCode 문제 크롤링: {} - {}", basicProblem.getQuestionId(), titleSlug);

        try {
            // 상세 정보 API 호출
            Map<String, Object> detail = fetchProblemDetail(titleSlug);

            String description = extractDescription(detail);
            String sampleInput = extractSampleInput(detail);
            String sampleOutput = extractSampleOutput(detail);
            String constraints = extractConstraints(detail);

            return ProblemDocumentDto.builder()
                    .source("LEETCODE")
                    .externalId(basicProblem.getQuestionId())
                    .title(String.format("[LeetCode %s] %s",
                            basicProblem.getQuestionId(), basicProblem.getTitle()))
                    .description(description)
                    .difficulty(mapDifficulty(basicProblem.getDifficulty()))
                    .tags(basicProblem.getTagNames() != null ? basicProblem.getTagNames() : List.of())
                    .language("en")
                    .sampleInput(sampleInput)
                    .sampleOutput(sampleOutput)
                    .constraints(constraints)
                    .url(LEETCODE_URL + titleSlug)
                    .build();

        } catch (Exception e) {
            log.error("❌ LeetCode 크롤링 실패: {} - {}", titleSlug, e.getMessage());
            return createFallbackDocument(basicProblem);
        }
    }

    /**
     * 문제 상세 정보 API 호출
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchProblemDetail(String titleSlug) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/select")
                            .queryParam("titleSlug", titleSlug)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> Mono.empty())
                    .block();
        } catch (Exception e) {
            log.warn("상세 정보 조회 실패: {}", titleSlug);
            return Map.of();
        }
    }

    /**
     * 문제 설명 추출
     */
    private String extractDescription(Map<String, Object> detail) {
        if (detail == null) return "";

        Object content = detail.get("content");
        if (content != null) {
            // HTML 태그 제거 (간단한 처리)
            String text = content.toString()
                    .replaceAll("<[^>]*>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&amp;", "&")
                    .replaceAll("\\s+", " ")
                    .trim();
            return text;
        }

        // content가 없으면 question 필드 확인
        Object question = detail.get("question");
        if (question != null) {
            return question.toString();
        }

        return "";
    }

    /**
     * 예제 입력 추출
     */
    private String extractSampleInput(Map<String, Object> detail) {
        if (detail == null) return "";

        Object exampleTestcases = detail.get("exampleTestcases");
        if (exampleTestcases != null) {
            return exampleTestcases.toString();
        }

        return "";
    }

    /**
     * 예제 출력 추출
     */
    private String extractSampleOutput(Map<String, Object> detail) {
        // alfa-leetcode-api는 예제 출력을 직접 제공하지 않음
        // content에서 파싱해야 하는데, 복잡하므로 빈 문자열 반환
        return "";
    }

    /**
     * 제약 조건 추출
     */
    private String extractConstraints(Map<String, Object> detail) {
        if (detail == null) return "";

        // hints가 있으면 힌트 정보 반환
        Object hints = detail.get("hints");
        if (hints instanceof List<?> hintList && !hintList.isEmpty()) {
            return "Hints available: " + hintList.size();
        }

        return "";
    }

    /**
     * 난이도 매핑
     */
    private String mapDifficulty(String difficulty) {
        if (difficulty == null) return "EASY";
        return switch (difficulty.toUpperCase()) {
            case "EASY" -> "EASY";
            case "MEDIUM" -> "MEDIUM";
            case "HARD" -> "HARD";
            default -> "EASY";
        };
    }

    /**
     * 크롤링 실패 시 fallback 문서 생성
     */
    private ProblemDocumentDto createFallbackDocument(LeetCodeProblemDto problem) {
        return ProblemDocumentDto.builder()
                .source("LEETCODE")
                .externalId(problem.getQuestionId())
                .title(String.format("[LeetCode %s] %s",
                        problem.getQuestionId(), problem.getTitle()))
                .description("Problem description not available.")
                .difficulty(mapDifficulty(problem.getDifficulty()))
                .tags(problem.getTagNames() != null ? problem.getTagNames() : List.of())
                .language("en")
                .sampleInput("")
                .sampleOutput("")
                .constraints("")
                .url(LEETCODE_URL + problem.getTitleSlug())
                .build();
    }

    /**
     * 여러 문제 일괄 크롤링
     *
     * @param problems    크롤링할 문제 목록
     * @param delayMillis 요청 간 지연 시간 (Rate Limiting 방지)
     * @return 크롤링된 문제 문서 목록
     */
    public List<ProblemDocumentDto> crawlProblems(List<LeetCodeProblemDto> problems, long delayMillis) {
        List<ProblemDocumentDto> results = new ArrayList<>();

        for (int i = 0; i < problems.size(); i++) {
            LeetCodeProblemDto problem = problems.get(i);

            // 유료 문제 제외
            if (Boolean.TRUE.equals(problem.getIsPaidOnly())) {
                log.debug("⏭️  유료 문제 제외: {}", problem.getTitle());
                continue;
            }

            ProblemDocumentDto doc = crawlProblemDetail(problem);
            results.add(doc);

            log.info("📥 크롤링 진행: {}/{}", results.size(), problems.size());

            // Rate Limiting 방지
            if (i < problems.size() - 1 && delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("크롤링 중단됨");
                    break;
                }
            }
        }

        return results;
    }
}
