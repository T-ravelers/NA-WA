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

    // cursor가 null이면 첫 쪽이다. 다음 쪽이 있는지 알려면 화면에 쓸 개수보다 한 건 더
    // 달라고 해야 하므로, limit은 서비스가 이미 +1 해서 넘긴 값이다.
    List<Notification> findByRecipient(
        @Param("memberId") Long memberId,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
    );

    int countUnreadByRecipient(@Param("memberId") Long memberId);

    // 읽은 시각도 DB에게 "지금 몇 시냐"고 묻지 않고 애플리케이션이 넘긴 값을 쓴다.
    // 정산 완료 시각과 같은 이유다 — CI는 MySQL을 일부러 UTC로 띄워 이 의존을 드러낸다.
    int markAllRead(
        @Param("memberId") Long memberId,
        @Param("readAt") LocalDateTime readAt
    );

    // 아래 세 가지는 모두 recipient_member_id로 범위를 좁힌다. 알림 번호를 경로로 받게
    // 되면서 남의 번호를 적어 볼 수 있게 됐는데, 이 조건이 있으면 그런 요청은 0행을
    // 바꾸고 끝난다. 호출하는 쪽은 0과 1을 구분해 다른 응답을 내지 않는다 — 응답이
    // 갈리는 순간 그 차이가 "그 번호의 알림이 있다"는 사실을 알려 주기 때문이다.
    int markRead(
        @Param("memberId") Long memberId,
        @Param("notificationId") Long notificationId,
        @Param("readAt") LocalDateTime readAt
    );

    // 지우는 것도 행을 없애지 않고 deleted_at을 적는다. 조회 쿼리가 모두 이 컬럼을
    // 걸러 내므로 화면에서는 사라지고, 무엇이 언제 지워졌는지는 남는다.
    int softDelete(
        @Param("memberId") Long memberId,
        @Param("notificationId") Long notificationId,
        @Param("deletedAt") LocalDateTime deletedAt
    );

    int softDeleteAll(
        @Param("memberId") Long memberId,
        @Param("deletedAt") LocalDateTime deletedAt
    );

    void insertNotifications(@Param("notifications") List<Notification> notifications);

    SettlementNotificationSnapshot findSettlementSnapshot(
        @Param("settlementId") Long settlementId
    );

    List<SettlementMemberShare> findSettlementMemberShares(
        @Param("settlementId") Long settlementId
    );
}
