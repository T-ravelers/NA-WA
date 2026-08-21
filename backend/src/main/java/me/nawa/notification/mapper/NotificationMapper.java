package me.nawa.notification.mapper;

import java.time.LocalDateTime;
import java.util.List;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.SettlementMemberShare;
import me.nawa.notification.domain.SettlementNotificationSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 알림 적재·조회와, 알림 문장에 넣을 값을 정산에서 읽어 오는 영속성 계약이다. */
@Mapper
public interface NotificationMapper {

    List<Notification> findByRecipient(
        @Param("memberId") Long memberId,
        @Param("limit") int limit
    );

    int countUnreadByRecipient(@Param("memberId") Long memberId);

    // 읽은 시각도 DB에게 "지금 몇 시냐"고 묻지 않고 애플리케이션이 넘긴 값을 쓴다.
    // 정산 완료 시각과 같은 이유다 — CI는 MySQL을 일부러 UTC로 띄워 이 의존을 드러낸다.
    int markAllRead(
        @Param("memberId") Long memberId,
        @Param("readAt") LocalDateTime readAt
    );

    void insertNotifications(@Param("notifications") List<Notification> notifications);

    SettlementNotificationSnapshot findSettlementSnapshot(
        @Param("settlementId") Long settlementId
    );

    List<SettlementMemberShare> findSettlementMemberShares(
        @Param("settlementId") Long settlementId
    );
}
