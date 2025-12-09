package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.AlgoLevel;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 사용자 알고리즘 레벨 DTO
 */
@Data
public class UserAlgoLevelDto {
    private Long levelId;
    private Long userId;
    private AlgoLevel algoLevel;
    private int totalSolved;
    private int currentStreak;
    private int maxStreak;
    private LocalDateTime lastSolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
