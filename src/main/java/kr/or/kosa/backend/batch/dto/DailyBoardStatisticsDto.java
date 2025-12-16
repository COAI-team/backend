package kr.or.kosa.backend.batch.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 일간 게시판 통계 DTO
 *
 * 매일 배치 작업을 통해 집계되는 게시판별 통계 데이터
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyBoardStatisticsDto {

    /**
     * 통계 ID (PK)
     */
    private Long statisticsId;

    /**
     * 통계 날짜
     */
    private LocalDate statDate;

    /**
     * 게시판 타입 (free: 자유게시판, code: 코드게시판, algo: 알고리즘게시판)
     */
    private String boardType;

    /**
     * 해당 날짜에 작성된 게시글 수
     */
    private Integer postCount;

    /**
     * 해당 날짜 게시글들의 총 조회수
     */
    private Long totalViews;

    /**
     * 해당 날짜 게시글들의 평균 조회수
     */
    private Double avgViews;

    /**
     * 해당 날짜에 삭제된 게시글 수
     */
    private Integer deletedPostCount;

    /**
     * 통계 생성 일시
     */
    private java.time.LocalDateTime createdAt;
}