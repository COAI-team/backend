package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.DailyMissionDto;
import kr.or.kosa.backend.algorithm.dto.UserAlgoLevelDto;
import kr.or.kosa.backend.algorithm.dto.enums.MissionType;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.DailyMissionService;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 데일리 미션 컨트롤러
 */
@RestController
@RequestMapping("/algo/missions")
@RequiredArgsConstructor
@Slf4j
public class DailyMissionController {

    private final DailyMissionService dailyMissionService;

    /**
     * 오늘의 미션 조회
     * GET /api/algo/missions/today
     */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<DailyMissionDto>>> getTodayMissions(
            @AuthenticationPrincipal JwtAuthentication authentication,
            @RequestParam(required = false) Long testUserId) {

        Long userId = getUserId(authentication, testUserId);

        List<DailyMissionDto> missions = dailyMissionService.getTodayMissions(userId);
        log.info("오늘의 미션 조회 - userId: {}, 미션 수: {}", userId, missions.size());

        return ResponseEntity.ok(ApiResponse.success(missions));
    }

    /**
     * 미션 완료 처리
     * POST /api/algo/missions/complete
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeMission(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long testUserId = request.get("testUserId") != null
                ? Long.valueOf(request.get("testUserId").toString()) : null;
        Long userId = getUserId(authentication, testUserId);

        String missionTypeStr = (String) request.get("missionType");
        if (missionTypeStr == null) {
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }

        MissionType missionType;
        try {
            missionType = MissionType.valueOf(missionTypeStr);
        } catch (IllegalArgumentException e) {
            throw new CustomBusinessException(AlgoErrorCode.MISSION_TYPE_INVALID);
        }

        DailyMissionService.MissionCompleteResult result = dailyMissionService.completeMission(userId, missionType);

        if (!result.success()) {
            if (result.message().contains("찾을 수 없")) {
                throw new CustomBusinessException(AlgoErrorCode.MISSION_NOT_FOUND);
            } else {
                throw new CustomBusinessException(AlgoErrorCode.MISSION_ALREADY_COMPLETED);
            }
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("success", true);
        responseData.put("message", result.message());
        responseData.put("rewardPoints", result.rewardPoints());

        log.info("미션 완료 - userId: {}, type: {}, reward: {}P", userId, missionType, result.rewardPoints());
        return ResponseEntity.ok(ApiResponse.success(responseData));
    }

    /**
     * 사용량 정보 조회
     * GET /api/algo/missions/usage
     */
    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<DailyMissionService.UsageInfoResult>> getUsageInfo(
            @AuthenticationPrincipal JwtAuthentication authentication,
            @RequestParam(required = false) Long testUserId) {

        Long userId = getUserId(authentication, testUserId);

        DailyMissionService.UsageInfoResult usageInfo = dailyMissionService.getUsageInfo(userId);
        log.debug("사용량 조회 - userId: {}, total: {}, remaining: {}",
                userId, usageInfo.totalUsage(), usageInfo.remaining());

        return ResponseEntity.ok(ApiResponse.success(usageInfo));
    }

    /**
     * 사용자 알고리즘 레벨 조회
     * GET /api/algo/missions/level
     */
    @GetMapping("/level")
    public ResponseEntity<ApiResponse<UserAlgoLevelDto>> getUserLevel(
            @AuthenticationPrincipal JwtAuthentication authentication,
            @RequestParam(required = false) Long testUserId) {

        Long userId = getUserId(authentication, testUserId);

        UserAlgoLevelDto level = dailyMissionService.getOrCreateUserLevel(userId);
        log.debug("레벨 조회 - userId: {}, level: {}", userId, level.getAlgoLevel());

        return ResponseEntity.ok(ApiResponse.success(level));
    }

    /**
     * 인증 객체에서 사용자 ID 추출 (테스트용 userId 우선)
     */
    private Long getUserId(JwtAuthentication authentication, Long testUserId) {
        // 테스트용 userId가 있으면 사용 (개발 환경 전용)
        if (testUserId != null) {
            log.warn("테스트 모드: testUserId={} 사용", testUserId);
            return testUserId;
        }

        if (authentication == null) {
            throw new CustomBusinessException(AlgoErrorCode.LOGIN_REQUIRED);
        }
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        return userDetails.id().longValue();
    }
}
