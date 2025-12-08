package kr.or.kosa.backend.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record AdminUserDetailResponseDto(
    Long userId, // 유저 아이디
    String userEmail, // 유저 이메일
    String userName, // 유저 이름
    String userNickName, // 유저 닉네임
    String userRole, // 유저 권한
    int userGrade, // 유저 등급
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime userCreateAt, // 가입일자
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime userDeleteAt, // 탈퇴일
    int userPoint, // 보유 포인트
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime userSubscribeStart,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime userSubscribeEnd,
    String subscriptionStatus
) {
    // 💡 커스텀 생성자 (null 처리 및 변환용)
    public AdminUserDetailResponseDto(
        Long userId,
        String userEmail,
        String userName,
        String userNickName,
        String userRole,
        int userGrade,
        LocalDateTime userCreateAt,
        LocalDateTime userDeleteAt,
        Integer userPoint,
        LocalDateTime userSubscribeStart,
        LocalDateTime userSubscribeEnd,
        String subscriptionStatus
    ) {
        this(
            userId,
            userEmail,
            userName,
            userNickName,
            userRole,
            userGrade, // null이면 기본 등급 1
            userCreateAt,
            userDeleteAt,
            userPoint != null ? userPoint : 0, // null이면 0포인트
            userSubscribeStart,
            userSubscribeEnd,
            subscriptionStatus
        );
    }
}




