package me.nawa.notification.mapper;

import java.time.LocalDateTime;
import java.util.List;
import me.nawa.notification.domain.SettlementReminderCriteria;
import me.nawa.notification.domain.SettlementReminderTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 미납 리마인더 대상을 고르고, 보낸 기록을 남기는 영속성 계약이다.
 *
 * 대상은 두 번에 나눠 읽는다. 잠그지 않고 번호만 고르는 {@link #findCandidateIds}와, 트랜잭션
 * 안에서 같은 조건으로 다시 확인하며 잠그는 {@link #lockTargets}다. 두 쿼리는 조건 조각을
 * 공유하므로 "후보인데 잠그면 안 되는" 어긋남이 생기지 않는다.
 */
@Mapper
public interface SettlementReminderMapper {

    /**
     * 기준에 맞는 구성원 번호를 오름차순으로 {@code limit}개 읽는다. 잠그지 않는다.
     *
     * {@code afterId}보다 큰 번호부터 읽는 키셋 방식이라, 앞 묶음을 건너뛰기 위해 OFFSET만큼
     * 읽어 버리는 비용이 없다. 처음에는 0을 준다.
     */
    List<Long> findCandidateIds(
        @Param("criteria") SettlementReminderCriteria criteria,
        @Param("afterId") long afterId,
        @Param("limit") int limit
    );

    /**
     * 주어진 번호 중 지금도 기준에 맞는 구성원을 잠그고, 알림에 넣을 값과 함께 읽는다.
     *
     * 호출한 트랜잭션이 끝날 때까지 잠금이 이어지므로 트랜잭션 안에서만 부른다. 다른
     * 트랜잭션이 이미 잡은 행은 기다리지 않고 건너뛴다. 후보를 고른 뒤에 낸 사람이나 막
     * 알림을 받은 사람은 같은 조건으로 다시 걸러진다.
     *
     * READ COMMITTED에서 불러야 한다. REPEATABLE READ의 잠금 읽기는 훑은 범위에 갭 잠금을
     * 잡아서, 그 사이에 새 정산을 만드는 INSERT를 막을 수 있다.
     */
    List<SettlementReminderTarget> lockTargets(
        @Param("criteria") SettlementReminderCriteria criteria,
        @Param("ids") List<Long> ids
    );

    /**
     * 마지막 리마인더 시각과 횟수를 남긴다. 시각은 앱이 넘긴 값을 그대로 적는다.
     *
     * 이 기록이 "이미 보냈다"의 근거이므로 알림 적재와 같은 트랜잭션에서 부른다.
     */
    int markReminded(
        @Param("ids") List<Long> ids,
        @Param("remindedAt") LocalDateTime remindedAt
    );
}
