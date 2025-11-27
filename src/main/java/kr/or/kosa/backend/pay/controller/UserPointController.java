package kr.or.kosa.backend.pay.controller;

import kr.or.kosa.backend.pay.entity.UserPoint;
import kr.or.kosa.backend.pay.repository.PointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:5173")
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
            @RequestParam(value = "userId", required = false) String userId
    ) {
        String finalUserId =
                (userId == null || userId.isBlank()) ? "TEST_USER_001" : userId;

        UserPoint userPoint = pointMapper.findUserPointByUserId(finalUserId)
                .orElse(UserPoint.builder()
                        .userId(finalUserId)
                        .balance(BigDecimal.ZERO)
                        .build());

        Map<String, Object> body = new HashMap<>();
        body.put("userId", finalUserId);
        body.put("points", userPoint.getBalance());
        return ResponseEntity.ok(body);
    }
}
