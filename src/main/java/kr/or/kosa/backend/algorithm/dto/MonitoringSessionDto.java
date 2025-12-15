package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 모니터링 세션 DTO
 * 데이터베이스 테이블: MONITORING_SESSIONS
 *
 * 집중 모드(FOCUS)에서만 생성됨
 * 모니터링은 점수에 반영되지 않고, 경고/정보 제공 목적으로만 사용
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringSessionDto {

    // 기본 식별자
    private String sessionId;
    private Long userId;
    private Long algoProblemId;
    private Long algosubmissionId;

    // 세션 상태 및 시간
    private SessionStatus sessionStatus;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    // 시간 제한 설정
    private Integer timeLimitMinutes;
    private Integer remainingSeconds;
    private Boolean autoSubmitted;

    // 위반 유형별 카운트
    private Integer gazeAwayCount;       // 시선 이탈
    private Integer sleepingCount;       // 졸음 감지
    private Integer noFaceCount;         // 얼굴 미감지 (자리비움)
    private Integer maskDetectedCount;   // 마스크 착용
    private Integer multipleFacesCount;  // 복수 인원
    private Integer mouseLeaveCount;     // 마우스 화면 이탈
    private Integer tabSwitchCount;      // 탭/브라우저 전환
    private Integer fullscreenExitCount; // 전체화면 해제

    // 집계
    private Integer totalViolations;
    private Integer warningShownCount;

    /**
     * 모든 위반 카운트 합계 계산
     */
    public int calculateTotalViolations() {
        return (gazeAwayCount != null ? gazeAwayCount : 0)
             + (sleepingCount != null ? sleepingCount : 0)
             + (noFaceCount != null ? noFaceCount : 0)
             + (maskDetectedCount != null ? maskDetectedCount : 0)
             + (multipleFacesCount != null ? multipleFacesCount : 0)
             + (mouseLeaveCount != null ? mouseLeaveCount : 0)
             + (tabSwitchCount != null ? tabSwitchCount : 0)
             + (fullscreenExitCount != null ? fullscreenExitCount : 0);
    }

    /**
     * 세션 지속 시간 계산 (초 단위)
     */
    public Long getDurationSeconds() {
        if (startedAt == null) {
            return null;
        }
        LocalDateTime end = (endedAt != null) ? endedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, end).getSeconds();
    }

    /**
     * 시간 초과 여부 확인
     */
    public boolean isTimedOut() {
        return SessionStatus.TIMEOUT.equals(sessionStatus);
    }

    /**
     * 세션 활성 상태 확인
     */
    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(sessionStatus);
    }

    /**
     * 특정 위반 유형의 카운트 증가
     */
    public void incrementViolation(String violationType) {
        switch (violationType) {
            case "GAZE_AWAY":
                this.gazeAwayCount = (this.gazeAwayCount != null ? this.gazeAwayCount : 0) + 1;
                break;
            case "SLEEPING":
                this.sleepingCount = (this.sleepingCount != null ? this.sleepingCount : 0) + 1;
                break;
            case "NO_FACE":
                this.noFaceCount = (this.noFaceCount != null ? this.noFaceCount : 0) + 1;
                break;
            case "MASK_DETECTED":
                this.maskDetectedCount = (this.maskDetectedCount != null ? this.maskDetectedCount : 0) + 1;
                break;
            case "MULTIPLE_FACES":
                this.multipleFacesCount = (this.multipleFacesCount != null ? this.multipleFacesCount : 0) + 1;
                break;
            case "MOUSE_LEAVE":
                this.mouseLeaveCount = (this.mouseLeaveCount != null ? this.mouseLeaveCount : 0) + 1;
                break;
            case "TAB_SWITCH":
                this.tabSwitchCount = (this.tabSwitchCount != null ? this.tabSwitchCount : 0) + 1;
                break;
            case "FULLSCREEN_EXIT":
                this.fullscreenExitCount = (this.fullscreenExitCount != null ? this.fullscreenExitCount : 0) + 1;
                break;
        }
        this.totalViolations = calculateTotalViolations();
    }
}
