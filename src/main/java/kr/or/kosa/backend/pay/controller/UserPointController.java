package kr.or.kosa.backend.pay.controller;

import kr.or.kosa.backend.pay.entity.UserPoint;
import kr.or.kosa.backend.pay.entity.PointHistory;
import kr.or.kosa.backend.pay.repository.PointMapper;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = {"http://localhost:5173", "https://localhost:5173"})
@RequiredArgsConstructor
public class UserPointController {

    private final PointMapper pointMapper;

    /**
     * 테스트용 포인트 조회 API
     * - 실제 서비스에서는 인증 정보(SecurityContext)에서 userId를 꺼내 쓰면 됨
     * - 지금은 없으니까, 쿼리파라미터 없으면 TEST_USER_001 기준으로 조회
     */
    @GetMapping("/me/points")
    public ResponseEntity<Map<String, Object>> getMyPoints(
            @AuthenticationPrincipal JwtUserDetails user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "UNAUTHORIZED", "message", "로그인이 필요합니다."));
        }
        Long finalUserId = user.id();

        UserPoint userPoint = pointMapper.findUserPointByUserId(finalUserId)
                .orElse(UserPoint.builder()
                        .userId(finalUserId)
                        .balance(BigDecimal.ZERO)
                        .build());

        List<PointHistory> histories = pointMapper.findPointHistoryByUserId(finalUserId);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", finalUserId);
        body.put("points", userPoint.getBalance());
        body.put("history", histories);
        return ResponseEntity.ok(body);
    }
}
