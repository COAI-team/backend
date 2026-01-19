package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.AlgoLevel;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 사용자 알고리즘 레벨 DTO
 *
 * 변경사항 (2025-12-17): XP 기반 레벨 시스템 도입
 * - totalXp 필드 추가: 총 경험치
 * - algoLevel은 totalXp로부터 자동 계산됨
 * - 헬퍼 메서드: getXpToNextLevel(), getProgressInLevel()
 */
@Data
public class UserAlgoLevelDto {
    private Long levelId;
    private Long userId;
    private AlgoLevel algoLevel;

    /**
     * 총 경험치 (XP)
     * - 문제 풀이 시 획득한 XP 누적
     * - 레벨은 이 값으로 자동 계산됨
     */
    private int totalXp;

    private int totalSolved;
    private int currentStreak;
    private int maxStreak;
    private LocalDateTime lastSolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * XP로 레벨 계산 및 동기화
     * DB에서 로드 후 또는 XP 변경 후 호출
     */
    public void syncLevelFromXp() {
        this.algoLevel = AlgoLevel.fromXp(this.totalXp);
    }

    /**
     * 다음 레벨까지 필요한 XP
     */
    public int getXpToNextLevel() {
        if (algoLevel == null) {
            syncLevelFromXp();
        }
        return algoLevel.getXpToNextLevel(totalXp);
    }

    /**
     * 현재 레벨 내 진행률 (0.0 ~ 1.0)
     */
    public double getProgressInLevel() {
        if (algoLevel == null) {
            syncLevelFromXp();
        }
        return algoLevel.getProgressInLevel(totalXp);
    }

    /**
     * XP 추가 및 레벨 동기화
     * @param xp 추가할 XP
     * @return 레벨업 여부
     */
    public boolean addXp(int xp) {
        AlgoLevel previousLevel = this.algoLevel;
        this.totalXp += xp;
        syncLevelFromXp();
        return previousLevel != this.algoLevel;
    }
}
