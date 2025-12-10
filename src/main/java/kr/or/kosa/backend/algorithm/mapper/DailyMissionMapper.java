package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.dto.DailyMissionDto;
import kr.or.kosa.backend.algorithm.dto.UserAlgoLevelDto;
import kr.or.kosa.backend.algorithm.dto.enums.MissionType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 데일리 미션 MyBatis Mapper
 */
@Mapper
public interface DailyMissionMapper {

    // ===== 데일리 미션 =====

    /**
     * 데일리 미션 생성
     */
    void insertMission(DailyMissionDto mission);

    /**
     * 사용자의 오늘 미션 조회
     */
    List<DailyMissionDto> findTodayMissions(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 특정 미션 조회
     */
    DailyMissionDto findMission(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("missionType") MissionType missionType
    );

    /**
     * 미션 완료 처리
     */
    void completeMission(@Param("missionId") Long missionId);

    /**
     * 모든 활성 사용자 ID 조회 (미션 생성용)
     */
    List<Long> findAllActiveUserIds();

    /**
     * 난이도별 랜덤 문제 ID 조회
     */
    Long findRandomProblemIdByDifficulty(@Param("difficulty") String difficulty);

    /**
     * 오늘 날짜 + 난이도로 이미 할당된 문제 ID 조회 (같은 레벨 유저에게 같은 문제 배정용)
     */
    Long findTodayProblemIdByDifficulty(@Param("date") LocalDate date, @Param("difficulty") String difficulty);

    // ===== 사용자 레벨 =====

    /**
     * 사용자 레벨 조회
     */
    UserAlgoLevelDto findUserLevel(@Param("userId") Long userId);

    /**
     * 사용자 레벨 생성
     */
    void insertUserLevel(UserAlgoLevelDto level);

    /**
     * 사용자 레벨 업데이트
     */
    void updateUserLevel(UserAlgoLevelDto level);

    /**
     * 문제 풀이 완료 시 통계 업데이트
     */
    void incrementSolvedCount(@Param("userId") Long userId);

    // ===== 일일 사용량 (DB 백업용) =====

    /**
     * 일일 사용량 UPSERT
     */
    void upsertDailyUsage(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("generateCount") int generateCount,
            @Param("solveCount") int solveCount
    );

    /**
     * 일일 사용량 조회
     */
    Integer getTotalDailyUsage(@Param("userId") Long userId, @Param("date") LocalDate date);
}
