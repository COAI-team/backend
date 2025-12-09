package kr.or.kosa.backend.algorithm.dto.request;

import lombok.Data;

/**
 * LeetCode 문제 크롤링 요청 DTO
 */
@Data
public class LeetCodeCrawlRequestDto {
    private Integer count;          // 가져올 문제 수
    private Boolean useAiRewrite;   // AI 재서술 사용 여부
    private String difficulty;      // 난이도 (EASY, MEDIUM, HARD)
}
