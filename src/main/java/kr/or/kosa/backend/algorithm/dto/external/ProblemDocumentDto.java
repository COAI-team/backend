package kr.or.kosa.backend.algorithm.dto.external;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Vector DB에 저장할 알고리즘 문제 문서 DTO
 * RAG 기반 Few-shot 학습에 사용
 */
@Getter
@Builder
public class ProblemDocumentDto {

    private String source;          // BOJ, LEETCODE
    private String externalId;      // 원본 사이트 문제 ID
    private String title;           // 문제 제목
    private String description;     // 문제 설명
    private String difficulty;      // BRONZE, SILVER, GOLD, PLATINUM, EASY, MEDIUM, HARD
    private List<String> tags;      // 알고리즘 태그
    private String language;        // ko, en
    private String sampleInput;     // 예제 입력
    private String sampleOutput;    // 예제 출력
    private String constraints;     // 제약 조건
    private String url;             // 원본 문제 URL

    /**
     * Vector DB 저장용 텍스트 콘텐츠 생성
     * 이 텍스트가 임베딩되어 유사도 검색에 사용됨
     */
    public String toEmbeddingContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("Problem: ").append(title).append("\n");
        sb.append("Difficulty: ").append(difficulty).append("\n");
        sb.append("Tags: ").append(String.join(", ", tags)).append("\n\n");
        sb.append("Description:\n").append(description).append("\n\n");

        if (sampleInput != null && !sampleInput.isEmpty()) {
            sb.append("Sample Input:\n").append(sampleInput).append("\n\n");
        }
        if (sampleOutput != null && !sampleOutput.isEmpty()) {
            sb.append("Sample Output:\n").append(sampleOutput).append("\n");
        }
        if (constraints != null && !constraints.isEmpty()) {
            sb.append("\nConstraints:\n").append(constraints);
        }

        return sb.toString();
    }

    /**
     * Vector DB 메타데이터 생성
     * 필터링 검색에 사용
     */
    public Map<String, Object> toMetadata() {
        return Map.of(
                "source", source,
                "externalId", externalId,
                "title", title,
                "difficulty", difficulty,
                "tags", String.join(",", tags),
                "language", language,
                "url", url != null ? url : ""
        );
    }
}
