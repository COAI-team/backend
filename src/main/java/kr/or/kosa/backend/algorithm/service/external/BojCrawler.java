package kr.or.kosa.backend.algorithm.service.external;

import kr.or.kosa.backend.algorithm.dto.external.ProblemDocumentDto;
import kr.or.kosa.backend.algorithm.dto.external.SolvedAcProblemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 백준 온라인 저지(BOJ) 문제 크롤러
 * Jsoup을 사용하여 문제 상세 정보(설명, 예제 입출력) 수집
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BojCrawler {

    private static final String BOJ_BASE_URL = "https://www.acmicpc.net/problem/";
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /**
     * BOJ 문제 상세 정보 크롤링
     *
     * @param solvedAcProblem solved.ac API에서 가져온 문제 정보
     * @return Vector DB 저장용 문제 문서
     */
    public ProblemDocumentDto crawlProblemDetail(SolvedAcProblemDto solvedAcProblem) {
        Long problemId = solvedAcProblem.getProblemId();
        String url = BOJ_BASE_URL + problemId;

        log.info("🔍 BOJ 문제 크롤링: {} - {}", problemId, url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // 문제 제목
            String title = extractTitle(doc, solvedAcProblem);

            // 문제 설명
            String description = extractDescription(doc);

            // 예제 입출력
            List<String[]> samples = extractSamples(doc);
            String sampleInput = samples.isEmpty() ? "" : samples.get(0)[0];
            String sampleOutput = samples.isEmpty() ? "" : samples.get(0)[1];

            // 제약 조건 (시간/메모리 제한)
            String constraints = extractConstraints(doc);

            // 태그 (한국어)
            List<String> tags = solvedAcProblem.getKoreanTagNames();

            return ProblemDocumentDto.builder()
                    .source("BOJ")
                    .externalId(String.valueOf(problemId))
                    .title(title)
                    .description(description)
                    .difficulty(solvedAcProblem.getDifficultyEnum())
                    .tags(tags != null ? tags : List.of())
                    .language("ko")
                    .sampleInput(sampleInput)
                    .sampleOutput(sampleOutput)
                    .constraints(constraints)
                    .url(url)
                    .build();

        } catch (IOException e) {
            log.error("❌ BOJ 크롤링 실패: {} - {}", problemId, e.getMessage());
            return createFallbackDocument(solvedAcProblem);
        }
    }

    /**
     * 문제 제목 추출
     */
    private String extractTitle(Document doc, SolvedAcProblemDto solvedAc) {
        Element titleElement = doc.selectFirst("#problem_title");
        if (titleElement != null) {
            return String.format("[BOJ %d] %s", solvedAc.getProblemId(), titleElement.text());
        }
        return String.format("[BOJ %d] %s", solvedAc.getProblemId(),
                solvedAc.getTitleKo() != null ? solvedAc.getTitleKo() : solvedAc.getTitle());
    }

    /**
     * 문제 설명 추출
     */
    private String extractDescription(Document doc) {
        StringBuilder sb = new StringBuilder();

        // 문제 설명
        Element problemDesc = doc.selectFirst("#problem_description");
        if (problemDesc != null) {
            sb.append(problemDesc.text()).append("\n\n");
        }

        // 입력 설명
        Element inputDesc = doc.selectFirst("#problem_input");
        if (inputDesc != null) {
            sb.append("입력:\n").append(inputDesc.text()).append("\n\n");
        }

        // 출력 설명
        Element outputDesc = doc.selectFirst("#problem_output");
        if (outputDesc != null) {
            sb.append("출력:\n").append(outputDesc.text());
        }

        return sb.toString().trim();
    }

    /**
     * 예제 입출력 추출
     */
    private List<String[]> extractSamples(Document doc) {
        List<String[]> samples = new ArrayList<>();

        // 예제 입력/출력 쌍 찾기
        for (int i = 1; i <= 10; i++) {
            Element sampleInput = doc.selectFirst("#sample-input-" + i);
            Element sampleOutput = doc.selectFirst("#sample-output-" + i);

            if (sampleInput != null && sampleOutput != null) {
                samples.add(new String[]{
                        sampleInput.text().trim(),
                        sampleOutput.text().trim()
                });
            } else {
                break;
            }
        }

        return samples;
    }

    /**
     * 제약 조건 추출 (시간/메모리 제한)
     */
    private String extractConstraints(Document doc) {
        StringBuilder sb = new StringBuilder();

        Element problemInfo = doc.selectFirst("#problem-info");
        if (problemInfo != null) {
            Elements rows = problemInfo.select("tr");
            for (Element row : rows) {
                Elements tds = row.select("td");
                if (tds.size() >= 2) {
                    String label = row.select("th").text();
                    String value = tds.first().text();
                    if (label.contains("시간") || label.contains("메모리")) {
                        sb.append(label).append(": ").append(value).append("\n");
                    }
                }
            }
        }

        // 제한 조건 섹션
        Element limitSection = doc.selectFirst("#problem_limit");
        if (limitSection != null) {
            sb.append("\n").append(limitSection.text());
        }

        return sb.toString().trim();
    }

    /**
     * 크롤링 실패 시 fallback 문서 생성
     */
    private ProblemDocumentDto createFallbackDocument(SolvedAcProblemDto solvedAc) {
        return ProblemDocumentDto.builder()
                .source("BOJ")
                .externalId(String.valueOf(solvedAc.getProblemId()))
                .title(String.format("[BOJ %d] %s", solvedAc.getProblemId(),
                        solvedAc.getTitleKo() != null ? solvedAc.getTitleKo() : solvedAc.getTitle()))
                .description("문제 설명을 가져올 수 없습니다.")
                .difficulty(solvedAc.getDifficultyEnum())
                .tags(solvedAc.getKoreanTagNames() != null ? solvedAc.getKoreanTagNames() : List.of())
                .language("ko")
                .sampleInput("")
                .sampleOutput("")
                .constraints("")
                .url(BOJ_BASE_URL + solvedAc.getProblemId())
                .build();
    }

    /**
     * 여러 문제 일괄 크롤링
     *
     * @param problems    크롤링할 문제 목록
     * @param delayMillis 요청 간 지연 시간 (Rate Limiting 방지)
     * @return 크롤링된 문제 문서 목록
     */
    public List<ProblemDocumentDto> crawlProblems(List<SolvedAcProblemDto> problems, long delayMillis) {
        List<ProblemDocumentDto> results = new ArrayList<>();

        for (int i = 0; i < problems.size(); i++) {
            SolvedAcProblemDto problem = problems.get(i);

            ProblemDocumentDto doc = crawlProblemDetail(problem);
            results.add(doc);

            log.info("📥 크롤링 진행: {}/{}", i + 1, problems.size());

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
