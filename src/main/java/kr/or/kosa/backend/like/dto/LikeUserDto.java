package kr.or.kosa.backend.like.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 좋아요 누른 유저를 확인하기 위한 dto

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeUserDto {
    private Long userId;
    private String userNickname;
    private String userImage;
    private LocalDateTime likedAt;
}
