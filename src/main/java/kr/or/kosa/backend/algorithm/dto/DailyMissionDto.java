package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.MissionType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 데일리 미션 DTO
 */
@Data
public class DailyMissionDto {
    private Long missionId;
    private Long userId;
    private LocalDate missionDate;
    private MissionType missionType;
    private Long problemId;           // PROBLEM_SOLVE 타입일 때 풀어야 할 문제 ID
    private boolean isCompleted;
    private int rewardPoints;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    // 조인된 문제 정보 (선택적)
    private String problemTitle;
    private String problemDifficulty;
}
