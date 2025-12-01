package kr.or.kosa.backend.notification.service;

import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.notification.domain.Notification;
import kr.or.kosa.backend.notification.domain.NotificationType;
import kr.or.kosa.backend.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationMapper notificationMapper;

    @Transactional
    public void sendNotification(
            Long recipientId,
            Long senderId,
            NotificationType notificationType,
            ReferenceType referenceType,
            Long referenceId
    ) {
        // 자기 자신에게는 알림 보내지 않음
        if (recipientId.equals(senderId)) {
            return;
        }

        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .senderId(senderId)
                .notificationType(notificationType)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .message(notificationType.getMessageTemplate())
                .build();

        notificationMapper.insertNotification(notification);
    }

    public List<Notification> getUnreadNotifications(Integer userId) {
        return notificationMapper.selectUnreadNotifications(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationMapper.markAsRead(notificationId);
    }

    @Transactional
    public void deleteByReference(ReferenceType referenceType, Long referenceId) {
        notificationMapper.deleteByReference(referenceType, referenceId);
    }
}