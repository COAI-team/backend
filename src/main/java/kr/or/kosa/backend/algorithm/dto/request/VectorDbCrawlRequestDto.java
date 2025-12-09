package kr.or.kosa.backend.algorithm.dto.request;

import lombok.Data;

/**
 * Vector DB 문제 수집 요청 DTO
 */
@Data
public class VectorDbCrawlRequestDto {
    private String query;           // 검색 쿼리
    private Integer count;          // 수집할 문제 수
}
