package kr.or.kosa.backend.algorithm.service.external;

import kr.or.kosa.backend.algorithm.dto.external.SolvedAcProblemDto;
import kr.or.kosa.backend.algorithm.dto.external.SolvedAcSearchResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * solved.ac API 클라이언트
 * 백준 문제 정보를 가져오는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolvedAcApiClient {

    private static final String BASE_URL = "https://solved.ac/api/v3";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient = WebClient.builder()
            .baseUrl(BASE_URL)
            .build();

    /**
     * 백준 문제 검색
     *
     * @param query 검색 쿼리 (예: "*s", "tier:b")
     * @param page  페이지 번호 (1부터 시작)
     * @return 문제 목록
     */
    public List<SolvedAcProblemDto> searchProblems(String query, int page) {
        log.info("solved.ac API 호출: query={}, page={}", query, page);

        try {
            SolvedAcSearchResponseDto response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/problem")
                            .queryParam("query", query)
                            .queryParam("page", page)
                            .queryParam("sort", "id")
                            .queryParam("direction", "asc")
                            .build())
                    .retrieve()
                    .bodyToMono(SolvedAcSearchResponseDto.class)
                    .timeout(TIMEOUT)
                    .doOnError(e -> log.error("solved.ac API 오류: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .block();

            if (response != null && response.getItems() != null) {
                log.info("✅ {}개 문제 조회 성공", response.getItems().size());
                return response.getItems();
            }

            log.warn("⚠️ 응답이 비어있습니다.");
            return List.of();

        } catch (Exception e) {
            log.error("❌ solved.ac API 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 특정 문제 정보 조회
     *
     * @param problemId 문제 ID
     * @return 문제 정보
     */
    public SolvedAcProblemDto getProblem(Long problemId) {
        log.info("solved.ac 문제 조회: problemId={}", problemId);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/problem/show")
                            .queryParam("problemId", problemId)
                            .build())
                    .retrieve()
                    .bodyToMono(SolvedAcProblemDto.class)
                    .timeout(TIMEOUT)
                    .doOnError(e -> log.error("solved.ac API 오류: {}", e.getMessage()))
                    .block();

        } catch (Exception e) {
            log.error("❌ solved.ac 문제 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
