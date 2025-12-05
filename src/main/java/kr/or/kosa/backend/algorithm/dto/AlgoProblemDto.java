package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemSource;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알고리즘 문제 DTO
 * 데이터베이스 테이블: ALGO_PROBLEMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgoProblemDto {

    /**
     * 문제 고유 식별자 (AUTO_INCREMENT)
     */
    private Long algoProblemId;

    /**
     * 문제 제목
     */
    private String algoProblemTitle;

    /**
     * 문제 상세 설명
     */
    private String algoProblemDescription;

    /**
     * 문제 난이도
     */
    private ProblemDifficulty algoProblemDifficulty;

    /**
     * 문제 생성 출처
     */
    private ProblemSource algoProblemSource;

    /**
     * 문제 유형 (기본값: ALGORITHM)
     */
    private ProblemType problemType;

    /**
     * SQL 문제용 초기화 스크립트
     */
    private String initScript;

    /**
     * 시간 제한(ms) (기본값: 1000ms)
     */
    private Integer timelimit;

    /**
     * 메모리 제한(MB) (기본값: 256MB)
     */
    private Integer memorylimit;

    /**
     * 문제 생성자 ID
     */
    private Long algoCreater;

    /**
     * 생성 일시
     */
    private LocalDateTime algoCreatedAt;

    /**
     * 수정 일시
     */
    private LocalDateTime algoUpdatedAt;

    /**
     * 문제 태그 (쉼표로 구분된 문자열)
     */
    private String algoProblemTags;

    /**
     * 문제 활성화 상태 (1: 활성, 0: 비활성)
     */
    private Boolean algoProblemStatus;

    // === 추가 필드 (연관 데이터) ===

    /**
     * 연관된 테스트케이스 목록 (조인 시 사용)
     */
    private List<AlgoTestcaseDto> testcases;

    /**
     * 문제 통계 정보 (조인 시 사용)
     */
    private Integer totalAttempts;
    private Integer successCount;
    private Double averageScore;
}
