package kr.or.kosa.backend.algorithm.dto.request;

import lombok.Data;

/**
 * BOJ 문제 크롤링 요청 DTO
 */
@Data
public class BojCrawlRequestDto {
    private String query;           // 검색 쿼리 (예: "*s", "tier:b")
    private Integer count;          // 가져올 문제 수
    private Boolean useAiRewrite;   // AI 재서술 사용 여부
}
