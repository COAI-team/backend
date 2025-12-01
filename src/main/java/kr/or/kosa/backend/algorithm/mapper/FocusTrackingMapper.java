package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.domain.FocusSession;
import kr.or.kosa.backend.algorithm.domain.FocusSummary;
import kr.or.kosa.backend.algorithm.domain.ViolationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FocusTrackingMapper {

    // 세션 시작
    void startSession(FocusSession session);

    // 세션 종료 및 업데이트
    void updateSession(FocusSession session);

    // 위반 로그 기록
    void insertViolationLog(ViolationLog log);

    // 집중 요약 기록
    void insertFocusSummary(FocusSummary summary);

    // 세션 조회
    FocusSession findSessionById(String sessionId);

    // 특정 세션의 위반 로그 조회
    List<ViolationLog> findLogsBySessionId(String sessionId);

    // 사용자의 최근 세션 조회 (중복 실행 방지용)
    FocusSession findActiveSessionByUserId(@Param("userId") Long userId, @Param("problemId") Long problemId);
}
