package kr.or.kosa.backend.battle.controller;

import java.util.List;

import jakarta.validation.Valid;
import kr.or.kosa.backend.battle.dto.BattleRoomCreateRequest;
import kr.or.kosa.backend.battle.dto.BattleRoomResponse;
import kr.or.kosa.backend.battle.dto.BattleRoomJoinRequest;
import kr.or.kosa.backend.battle.dto.BattleRoomUpdateRequest;
import kr.or.kosa.backend.battle.service.BattleRoomService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/battle/rooms")
@RequiredArgsConstructor
public class BattleRoomController {

    private final BattleRoomService battleRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<BattleRoomResponse>> createRoom(
            @Valid @RequestBody BattleRoomCreateRequest request,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse response = battleRoomService.createRoom(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BattleRoomResponse>>> listRooms() {
        List<BattleRoomResponse> rooms = battleRoomService.listRooms();
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> getRoom(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.getRoom(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> getMyRoom(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return battleRoomService.findMyActiveRoom(userId)
                .map(room -> ResponseEntity.ok(ApiResponse.success(room)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> joinRoom(
            @PathVariable String roomId,
            @Valid @RequestBody(required = false) BattleRoomJoinRequest request,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.joinRoom(roomId, userId, request != null ? request.getPassword() : null);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> leaveRoom(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PostMapping("/{roomId}/surrender")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> surrender(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.surrender(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> updateRoom(
            @PathVariable String roomId,
            @Valid @RequestBody BattleRoomUpdateRequest request,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.updateSettings(roomId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PostMapping("/{roomId}/kick")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> kickGuest(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.kickGuest(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    @PostMapping("/{roomId}/reset")
    public ResponseEntity<ApiResponse<BattleRoomResponse>> resetRoom(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        Long userId = requireUserId(authentication);
        BattleRoomResponse room = battleRoomService.resetRoomForParticipant(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    private Long requireUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "???? ?????.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtUserDetails details) {
            return details.id();
        }

        Object detailsObj = authentication.getDetails();
        if (detailsObj instanceof JwtUserDetails details) {
            return details.id();
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "???? ?????.");
        }
    }
}

