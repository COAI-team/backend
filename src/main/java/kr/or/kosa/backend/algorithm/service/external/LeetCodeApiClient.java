package kr.or.kosa.backend.algorithm.service.external;

import kr.or.kosa.backend.algorithm.dto.external.LeetCodeProblemDto;
import kr.or.kosa.backend.algorithm.dto.external.LeetCodeProblemsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * alfa-leetcode-api 클라이언트
 * LeetCode 문제 정보를 가져오는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeetCodeApiClient {

    private static final String BASE_URL = "https://alfa-leetcode-api.onrender.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);  // 외부 API 응답 시간 고려

    private final WebClient webClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .build();

    /**
     * LeetCode 문제 목록 조회
     *
     * @param limit      가져올 문제 수 (최대 20)
     * @param tags       태그 필터 (예: "array+hash-table")
     * @param difficulty 난이도 필터 ("EASY", "MEDIUM", "HARD")
     * @return 문제 목록
     */
    public List<LeetCodeProblemDto> getProblems(int limit, String tags, String difficulty) {
        log.info("LeetCode API 호출: limit={}, tags={}, difficulty={}", limit, tags, difficulty);

        try {
            LeetCodeProblemsResponseDto response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/problems")
                                .queryParam("limit", Math.min(limit, 20));  // 최대 20개
                        if (tags != null && !tags.isBlank()) {
                            builder.queryParam("tags", tags);
                        }
                        if (difficulty != null && !difficulty.isBlank()) {
                            builder.queryParam("difficulty", difficulty);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(LeetCodeProblemsResponseDto.class)
                    .timeout(TIMEOUT)
                    .doOnError(e -> log.error("LeetCode API 오류: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .block();

            if (response != null && response.getProblemsetQuestionList() != null) {
                log.info("✅ {}개 문제 조회 성공", response.getProblemsetQuestionList().size());
                return response.getProblemsetQuestionList();
            }

            log.warn("⚠️ 응답이 비어있습니다.");
            return List.of();

        } catch (Exception e) {
            log.error("❌ LeetCode API 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 특정 LeetCode 문제 상세 정보 조회
     *
     * @param titleSlug 문제 slug (예: "two-sum")
     * @return 문제 상세 정보
     */
    public LeetCodeProblemDto getProblemDetail(String titleSlug) {
        log.info("LeetCode 문제 상세 조회: titleSlug={}", titleSlug);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/select")
                            .queryParam("titleSlug", titleSlug)
                            .build())
                    .retrieve()
                    .bodyToMono(LeetCodeProblemDto.class)
                    .timeout(TIMEOUT)
                    .doOnError(e -> log.error("LeetCode API 오류: {}", e.getMessage()))
                    .block();

        } catch (Exception e) {
            log.error("❌ LeetCode 문제 상세 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
