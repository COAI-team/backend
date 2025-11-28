package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.FocusSession;
import kr.or.kosa.backend.algorithm.domain.FocusSummary;
import kr.or.kosa.backend.algorithm.domain.ViolationLog;
import kr.or.kosa.backend.algorithm.mapper.FocusTrackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FocusTrackingService {

    private final FocusTrackingMapper focusTrackingMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_KEY_PREFIX = "focus:session:";
    private static final String VIOLATION_KEY_PREFIX = "focus:violation:";

    /**
     * 집중 세션 시작
     */
    @Transactional
    public FocusSession startSession(Long userId, Long problemId) {
        // 1. 이미 활성화된 세션이 있는지 확인
        FocusSession existingSession = focusTrackingMapper.findActiveSessionByUserId(userId, problemId);
        if (existingSession != null) {
            log.info("Active session already exists for user {} problem {}", userId, problemId);
            return existingSession;
        }

        // 2. 새 세션 생성 (UUID 사용) 및 DB 저장
        String sessionId = java.util.UUID.randomUUID().toString();

        FocusSession newSession = FocusSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .problemId(problemId)
                .status("ACTIVE")
                .violationCount(0)
                .startTime(LocalDateTime.now())
                .build();

        focusTrackingMapper.startSession(newSession);
        log.info("Started new focus session: {}", sessionId);

        // 3. Redis에 세션 상태 초기화 (24시간 유효)
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(redisKey, "ACTIVE", 24, TimeUnit.HOURS);

        return newSession;
    }

    /**
     * 실시간 이벤트 로깅 (Redis 저장)
     */
    public void logEvent(String sessionId, String eventType, Map<String, Object> eventData) {
        String redisKey = VIOLATION_KEY_PREFIX + sessionId;

        // 이벤트 데이터에 타임스탬프 추가
        eventData.put("timestamp", LocalDateTime.now().toString());
        eventData.put("type", eventType);

        // Redis List에 이벤트 추가 (오른쪽 push)
        redisTemplate.opsForList().rightPush(redisKey, eventData);

        // 키 만료 시간 갱신
        redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
    }

    /**
     * 세션 종료 및 결과 저장
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public FocusSession endSession(String sessionId) {
        // 1. DB에서 세션 조회
        FocusSession session = focusTrackingMapper.findSessionById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found");
        }

        // 이미 완료된 세션은 조용히 리턴 (중복 호출 허용)
        if (!"ACTIVE".equals(session.getStatus())) {
            log.info("Session already completed, returning existing session: {}", sessionId);
            return session;
        }

        // 2. Redis에서 위반 로그 조회
        String violationKey = VIOLATION_KEY_PREFIX + sessionId;
        List<Object> rawEvents = redisTemplate.opsForList().range(violationKey, 0, -1);

        int violationCount = 0;

        // 3. 위반 로그 DB 저장 및 카운트
        if (rawEvents != null) {
            for (Object eventObj : rawEvents) {
                Map<String, Object> event = (Map<String, Object>) eventObj;
                String type = (String) event.get("type");

                // 위반 이벤트만 카운트 및 저장 (시선 데이터 등은 제외할 수도 있음)
                if (isViolationEvent(type)) {
                    violationCount++;

                    // 세션 시작 시간과의 차이 계산 (초 단위)
                    long offsetSeconds = 0;
                    if (session.getStartTime() != null) {
                        offsetSeconds = java.time.Duration.between(session.getStartTime(), LocalDateTime.now())
                                .getSeconds();
                    }

                    // DB ENUM 타입으로 매핑
                    String dbViolationType = mapToDbViolationType(type);

                    ViolationLog log = ViolationLog.builder()
                            .sessionId(sessionId)
                            .userId(session.getUserId())
                            .violationType(dbViolationType)
                            .severity("HIGH") // 기본값 설정
                            .occurrenceCount(1)
                            .autoAction("WARNING") // 기본값 설정
                            .penaltyScore(5.0) // 기본 감점
                            .sessionTimeOffsetSeconds((int) offsetSeconds)
                            .build();

                    focusTrackingMapper.insertViolationLog(log);
                }
            }
        }

        // 4. 세션 정보 업데이트
        session.setEndTime(LocalDateTime.now());
        session.setStatus("COMPLETED");
        session.setViolationCount(violationCount);

        focusTrackingMapper.updateSession(session);

        // 5. 집중도 요약 생성 및 저장
        double focusRate = Math.max(0, 100.0 - (violationCount * 5.0));

        FocusSummary summary = FocusSummary.builder()
                .sessionId(sessionId)
                .userId(session.getUserId())
                .totalEvents(rawEvents != null ? rawEvents.size() : 0)
                .totalViolations(violationCount)
                .finalFocusScore(focusRate)
                .focusInPercentage(focusRate) // 간단하게 동일값 사용
                .focusGrade(calculateGrade(focusRate))
                .build();

        try {
            focusTrackingMapper.insertFocusSummary(summary);
        } catch (Exception e) {
            log.warn("Failed to insert focus summary (likely duplicate): {}", e.getMessage());
            // 이미 저장된 경우 무시하고 진행
        }

        // 6. Redis 데이터 정리
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        redisTemplate.delete(violationKey);

        return session;
    }

    private String calculateGrade(double score) {
        if (score >= 90)
            return "EXCELLENT";
        if (score >= 70)
            return "GOOD";
        if (score >= 50)
            return "FAIR";
        return "POOR";
    }

    private boolean isViolationEvent(String type) {
        return "GAZE_AWAY".equals(type) ||
                "TAB_SWITCH".equals(type) ||
                "NO_FACE".equals(type) ||
                "SCREEN_EXIT".equals(type) ||
                "FACE_MISSING".equals(type);
    }

    private String mapToDbViolationType(String type) {
        if ("GAZE_AWAY".equals(type))
            return "SCREEN_EXIT";
        if ("NO_FACE".equals(type))
            return "FACE_MISSING";
        return type; // TAB_SWITCH 등은 그대로 사용
    }
}
