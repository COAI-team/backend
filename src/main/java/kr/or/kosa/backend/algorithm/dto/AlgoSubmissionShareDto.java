package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 기존의 AlgoSubmissionDto에 유저 정보를
 * 추가로 담았다가 사이드 이팩트가 생길 것을 고려해서 생성
 * 공유된 알고리즘 제출 조회용 DTO
 * 사용자 정보가 포함된 공유 풀이 목록 전용
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgoSubmissionShareDto {

    // 제출 정보
    private Long submissionId;
    private Long problemId;
    private Long userId;
    private String sourceCode;
    private Integer languageId;
    private String languageName;

    // 채점 결과
    private String judgeResult;
    private Integer executionTime;
    private Integer memoryUsage;
    private Integer passedTestCount;
    private Integer totalTestCount;

    // AI 피드백
    private String aiFeedback;
    private String aiFeedbackStatus;

    // 점수
    private BigDecimal aiScore;
    private BigDecimal timeEfficiencyScore;
    private BigDecimal finalScore;

    // 시간
    private LocalDateTime submittedAt;

    // 사용자 정보 (JOIN으로 가져옴)
    private String userNickname;
    private String userImage;

    // 좋아요 정보
    private Boolean isLiked;
    private Integer likeCount;
}