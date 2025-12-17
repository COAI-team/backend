package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.service.AnalysisService;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final AnalysisService analysisService;
    private final UserMapper userMapper;

    // 1. MCP 토큰 발급/조회 (Authenticated User Only)
    // SecurityConfig에서 /api/mcp/token 은 authenticated() 로 설정해야 함 (현재는 permitAll이므로
    // 컨트롤러에서 체크 필요 또는 SecurityConfig 수정 권장)
    // 하지만 현재 세션에서 SecurityConfig 수정은 복잡하므로, 여기서는 Principal을 통해 유저 식별
    @PostMapping("/token")
    public ResponseEntity<?> getOrIssueToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        // Principal에서 userId 추출 (JwtAuthenticationFilter 로직에 따라 다름, 보통 UserDetails 구현체)
        // 여기서는 간단히 Principal.getName()이 email이나 userId라고 가정하거나,
        // 기존 Controller들의 패턴을 따름. (CustomUserDetails 캐스팅 등)

        // *가정: CustomUserDetails가 없으므로 SecurityContextHolder에서 userId를 어떻게 가져오는지 확인 필요.
        // DashboardPage에서 호출하므로 JWT 토큰이 넘어옴.

        // UserService/Controller 패턴 참고:
        // 보통 @AuthenticationPrincipal CustomUserDetails userDetails 사용.
        // 여기서는 안전하게 Authentication 객체 사용.

        try {
            // (임시) UserDetails에서 ID 추출 로직은 프로젝트마다 다름.
            // 여기서는 Principal이 "userId"(String)라고 가정 (JwtAuthenticationFilter 확인 필요)
            // 확인 결과: JwtAuthenticationFilter가 "userId"(Long)를 Principal로 넣거나 UserDetails를
            // 넣음.

            // 안전한 방법: UserMapper.findByEmail(authentication.getName()) 등 사용.
            // 하지만 더 확실한 건 JWT Filter가 userId를 Principal로 설정했는지 여부.
            // 일단 Email로 찾는다고 가정 (표준)
            // *그러나* JwtAuthenticationFilter를 확인하지 않았으므로,
            // 프론트엔드에서 userId를 보내주는 게 아니라, JWT Token 내의 ID를 써야 함.

            // [Fallback] 일단 DB에서 Email로 유저 찾기 (Authentication.getName()이 email인 경우)
            String principal = authentication.getName();
            Users user = userMapper.findByEmail(principal);

            // 만약 principal이 email이 아니라 userId라면?
            if (user == null && principal.matches("\\d+")) {
                user = userMapper.findById(Long.parseLong(principal));
            }

            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            if (user.getMcpToken() == null || user.getMcpToken().isEmpty()) {
                String newToken = UUID.randomUUID().toString();
                userMapper.updateMcpToken(user.getUserId(), newToken);
                user.setMcpToken(newToken);
            }

            return ResponseEntity.ok(Map.of("mcpToken", user.getMcpToken()));

        } catch (Exception e) {
            log.error("Token Issue Failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 2. MCP 토큰 재발급 (강제로 새 토큰 생성)
    @PutMapping("/token/regenerate")
    public ResponseEntity<?> regenerateToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        try {
            String principal = authentication.getName();
            Users user = userMapper.findByEmail(principal);

            if (user == null && principal.matches("\\d+")) {
                user = userMapper.findById(Long.parseLong(principal));
            }

            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            // 강제로 새 토큰 생성
            String newToken = UUID.randomUUID().toString();
            userMapper.updateMcpToken(user.getUserId(), newToken);

            log.info("MCP Token Regenerated for User {}", user.getUserId());
            return ResponseEntity.ok(Map.of("mcpToken", newToken));

        } catch (Exception e) {
            log.error("Token Regeneration Failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeCode(@RequestHeader(value = "X-MCP-Token", required = false) String token,
            @RequestBody Map<String, String> request) {

        // 1. 토큰 검증 (DB 조회)
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "Missing MCP Token"));
        }

        Users user = userMapper.findByMcpToken(token);
        if (user == null) {
            log.warn("MCP Access Denied: Invalid Token. Received: {}", token);
            return ResponseEntity.status(403).body(Map.of("error", "Invalid MCP Token"));
        }

        String code = request.get("code");
        String language = request.getOrDefault("language", "java");

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code content is required"));
        }

        try {
            log.info("MCP Analysis Request for User {} ({})", user.getUserId(), user.getUserNickname());
            String result = analysisService.analyzeRawCode(code, language, user.getUserId());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "result", result));
        } catch (Exception e) {
            log.error("MCP Processing Error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
