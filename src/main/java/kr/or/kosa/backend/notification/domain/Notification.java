package kr.or.kosa.backend.notification.domain;

import kr.or.kosa.backend.like.domain.ReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    private Long notificationId;
    private Long recipientId;
    private Long senderId;
    private NotificationType notificationType;
    private ReferenceType referenceType;
    private Long referenceId;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public void markAsRead() {
        this.isRead = true;
    }
}
