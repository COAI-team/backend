package kr.or.kosa.backend.notification.mapper;

import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.notification.domain.Notification;
import kr.or.kosa.backend.notification.domain.NotificationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    // 알림 생성
    void insertNotification(Notification notification);

    // 사용자의 읽지 않은 알림 조회
    List<Notification> selectUnreadNotifications(@Param("recipientId") Integer recipientId);

    // 알림 읽음 처리
    void markAsRead(@Param("notificationId") Long notificationId);

    // 참조 대상의 모든 알림 삭제
    void deleteByReference(@Param("referenceType") ReferenceType referenceType,
                           @Param("referenceId") Long referenceId);
}
