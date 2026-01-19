package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.dto.MonitoringSessionDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 모니터링 세션 Mapper
 * MONITORING_SESSIONS 테이블 접근
 *
 * 집중 모드(FOCUS)에서만 사용됨
 */
@Mapper
public interface MonitoringMapper {

    /**
     * 세션 시작 (새 세션 생성)
     */
    void startSession(MonitoringSessionDto session);

    /**
     * 세션 정보 업데이트 (종료 시 사용)
     */
    void updateSession(MonitoringSessionDto session);

    /**
     * 특정 위반 카운트 증가
     */
    void incrementViolationCount(
            @Param("sessionId") String sessionId,
            @Param("violationType") String violationType
    );

    /**
     * 경고 표시 횟수 증가
     */
    void incrementWarningCount(@Param("sessionId") String sessionId);

    /**
     * 세션 ID로 조회
     */
    MonitoringSessionDto findSessionById(@Param("sessionId") String sessionId);

    /**
     * 사용자의 활성 세션 조회 (중복 방지용)
     */
    MonitoringSessionDto findActiveSessionByUserId(
            @Param("userId") Long userId,
            @Param("problemId") Long problemId
    );

    /**
     * 제출 ID로 세션 조회
     */
    MonitoringSessionDto findSessionBySubmissionId(@Param("submissionId") Long submissionId);

    /**
     * 세션에 제출 ID 연결
     */
    void linkSubmission(
            @Param("sessionId") String sessionId,
            @Param("submissionId") Long submissionId
    );

    /**
     * 사용자의 세션 목록 조회
     */
    List<MonitoringSessionDto> findSessionsByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 세션 상태 변경 (ACTIVE → COMPLETED/TIMEOUT/TERMINATED)
     */
    void updateSessionStatus(
            @Param("sessionId") String sessionId,
            @Param("status") String status
    );

    /**
     * 자동 제출 플래그 설정
     */
    void markAsAutoSubmitted(@Param("sessionId") String sessionId);
}
