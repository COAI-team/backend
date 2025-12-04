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
    boolean userIsSubscribed,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime userSubscribeStart,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime userSubscribeEnd
) { }




