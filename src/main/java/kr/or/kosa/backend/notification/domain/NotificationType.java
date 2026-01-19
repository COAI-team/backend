package kr.or.kosa.backend.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    POST_COMMENT("님이 게시글에 댓글을 남겼습니다"),
    COMMENT_REPLY("님이 댓글에 답글을 남겼습니다"),
    POST_LIKE("님이 게시글을 좋아합니다"),
    COMMENT_LIKE("님이 댓글을 좋아합니다");

    private final String messageTemplate;
}
