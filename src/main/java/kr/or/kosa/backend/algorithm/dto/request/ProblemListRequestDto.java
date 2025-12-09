package kr.or.kosa.backend.algorithm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 문제 목록 조회 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemListRequestDto {

    // 필터 조건
    private String difficulty;      // 난이도 (BRONZE, SILVER, GOLD, PLATINUM)
    private String source;          // 출처 (AI_GENERATED, BOJ, CUSTOM)
    private String language;        // 언어 (Java, Python 등)
    private String status;          // 상태 (solved, unsolved)
    private String keyword;         // 검색 키워드
    private String sortBy;          // 정렬 기준 (latest, accuracy, popular)
    private String topic;           // 유형 (배열, DP, 그리디 등)

    // 페이징
    private Integer page;           // 페이지 번호 (1부터 시작)
    private Integer size;           // 페이지 크기

    // 사용자 ID (로그인한 사용자)
    private Long userId;

    // 계산된 필드
    public int getOffset() {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        return (page - 1) * size;
    }

    public int getLimit() {
        if (size == null || size < 1) {
            return 10;
        }
        return size;
    }
}