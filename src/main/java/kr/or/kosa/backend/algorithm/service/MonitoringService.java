package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.MonitoringSessionDto;
import kr.or.kosa.backend.algorithm.dto.enums.SessionStatus;
import kr.or.kosa.backend.algorithm.mapper.MonitoringMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 모니터링 서비스
 *
 * 집중 모드(FOCUS)에서 사용되는 모니터링 기능 제공
 * - 세션 시작/종료
 * - 위반 이벤트 실시간 처리
 * - 경고 팝업 트리거
 *
 * 주요 변경점:
 * - 모니터링 결과가 점수에 반영되지 않음 (정보 제공 및 경고 목적)
 * - Redis를 이용한 실시간 이벤트 처리
 * - 개별 위반 로그 대신 유형별 카운트 집계
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MonitoringMapper monitoringMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_KEY_PREFIX = "monitoring:session:";
    private static final String EVENT_KEY_PREFIX = "monitoring:events:";
    private static final int SESSION_TTL_HOURS = 24;

    /**
     * 모니터링 세션 시작 (집중 모드 진입 시 호출)
     *
     * @param userId 사용자 ID
     * @param problemId 문제 ID
     * @param timeLimitMinutes 제한 시간 (분)
     * @return 생성된 모니터링 세션
     */
    @Transactional
    public MonitoringSessionDto startSession(Long userId, Long problemId, Integer timeLimitMinutes) {
        // 1. 이미 활성화된 세션이 있는지 확인
        MonitoringSessionDto existingSession = monitoringMapper.findActiveSessionByUserId(userId, problemId);
        if (existingSession != null) {
            log.info("🔍 기존 활성 세션 발견 - userId: {}, problemId: {}, sessionId: {}",
                    userId, problemId, existingSession.getSessionId());
            return existingSession;
        }

        // 2. 새 세션 생성
        String sessionId = UUID.randomUUID().toString();

        MonitoringSessionDto newSession = MonitoringSessionDto.builder()
                .sessionId(sessionId)
                .userId(userId)
                .algoProblemId(problemId)
                .sessionStatus(SessionStatus.ACTIVE)
                .timeLimitMinutes(timeLimitMinutes)
                .startedAt(LocalDateTime.now())
                .gazeAwayCount(0)
                .sleepingCount(0)
                .noFaceCount(0)
                .maskDetectedCount(0)
                .multipleFacesCount(0)
                .mouseLeaveCount(0)
                .tabSwitchCount(0)
                .fullscreenExitCount(0)
                .totalViolations(0)
                .warningShownCount(0)
                .autoSubmitted(false)
                .build();

        monitoringMapper.startSession(newSession);
        log.info("✅ 모니터링 세션 시작 - sessionId: {}, timeLimitMinutes: {}", sessionId, timeLimitMinutes);

        // 3. Redis에 세션 상태 저장 (세션 만료 관리용)
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(redisKey, "ACTIVE", SESSION_TTL_HOURS, TimeUnit.HOURS);

        return newSession;
    }

    /**
     * 실시간 위반 이벤트 처리
     *
     * @param sessionId 세션 ID
     * @param violationType 위반 유형 (GAZE_AWAY, SLEEPING, NO_FACE, MASK_DETECTED,
     *                      MULTIPLE_FACES, MOUSE_LEAVE, TAB_SWITCH, FULLSCREEN_EXIT)
     * @param eventData 추가 이벤트 데이터
     */
    public void processViolation(String sessionId, String violationType, Map<String, Object> eventData) {
        log.info("⚠️ 위반 이벤트 감지 - sessionId: {}, type: {}", sessionId, violationType);

        // 1. DB에 위반 카운트 증가
        monitoringMapper.incrementViolationCount(sessionId, violationType);

        // 2. Redis에 이벤트 로그 저장 (실시간 모니터링용)
        String eventKey = EVENT_KEY_PREFIX + sessionId;
        eventData.put("timestamp", LocalDateTime.now().toString());
        eventData.put("violationType", violationType);
        redisTemplate.opsForList().rightPush(eventKey, eventData);
        redisTemplate.expire(eventKey, SESSION_TTL_HOURS, TimeUnit.HOURS);

        log.debug("📝 위반 이벤트 기록 완료 - sessionId: {}, type: {}", sessionId, violationType);
    }

    /**
     * 경고 표시 기록
     *
     * @param sessionId 세션 ID
     */
    public void recordWarningShown(String sessionId) {
        monitoringMapper.incrementWarningCount(sessionId);
        log.info("⚡ 경고 팝업 표시 - sessionId: {}", sessionId);
    }

    /**
     * 세션 종료 (정상 제출 시)
     *
     * @param sessionId 세션 ID
     * @param remainingSeconds 남은 시간 (초)
     * @return 종료된 세션 정보
     */
    @Transactional
    public MonitoringSessionDto endSession(String sessionId, Integer remainingSeconds) {
        MonitoringSessionDto session = monitoringMapper.findSessionById(sessionId);

        if (session == null) {
            log.warn("❌ 세션을 찾을 수 없음 - sessionId: {}", sessionId);
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // 이미 종료된 세션인 경우
        if (!session.isActive()) {
            log.info("ℹ️ 이미 종료된 세션 - sessionId: {}, status: {}", sessionId, session.getSessionStatus());
            return session;
        }

        // 세션 상태 업데이트
        session.setSessionStatus(SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        session.setRemainingSeconds(remainingSeconds);
        session.setAutoSubmitted(false);

        monitoringMapper.updateSession(session);
        log.info("✅ 세션 정상 종료 - sessionId: {}, totalViolations: {}",
                sessionId, session.getTotalViolations());

        // Redis 데이터 정리
        cleanupRedisData(sessionId);

        return session;
    }

    /**
     * 시간 초과 자동 제출 처리
     *
     * @param sessionId 세션 ID
     * @return 종료된 세션 정보
     */
    @Transactional
    public MonitoringSessionDto handleTimeout(String sessionId) {
        log.info("⏰ 시간 초과 자동 제출 처리 - sessionId: {}", sessionId);

        MonitoringSessionDto session = monitoringMapper.findSessionById(sessionId);

        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        if (!session.isActive()) {
            return session;
        }

        // 자동 제출 플래그 설정
        monitoringMapper.markAsAutoSubmitted(sessionId);

        // 업데이트된 세션 조회
        session = monitoringMapper.findSessionById(sessionId);
        log.info("⏰ 시간 초과 세션 종료 - sessionId: {}", sessionId);

        // Redis 데이터 정리
        cleanupRedisData(sessionId);

        return session;
    }

    /**
     * 세션에 제출 ID 연결
     *
     * @param sessionId 세션 ID
     * @param submissionId 제출 ID
     */
    @Transactional
    public void linkSubmission(String sessionId, Long submissionId) {
        monitoringMapper.linkSubmission(sessionId, submissionId);
        log.info("🔗 세션-제출 연결 완료 - sessionId: {}, submissionId: {}", sessionId, submissionId);
    }

    /**
     * 세션 조회
     *
     * @param sessionId 세션 ID
     * @return 세션 정보
     */
    public MonitoringSessionDto getSession(String sessionId) {
        return monitoringMapper.findSessionById(sessionId);
    }

    /**
     * 사용자의 활성 세션 조회
     *
     * @param userId 사용자 ID
     * @param problemId 문제 ID
     * @return 활성 세션 (없으면 null)
     */
    public MonitoringSessionDto getActiveSession(Long userId, Long problemId) {
        return monitoringMapper.findActiveSessionByUserId(userId, problemId);
    }

    /**
     * Redis 데이터 정리
     */
    private void cleanupRedisData(String sessionId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        redisTemplate.delete(EVENT_KEY_PREFIX + sessionId);
        log.debug("🧹 Redis 데이터 정리 완료 - sessionId: {}", sessionId);
    }
}
