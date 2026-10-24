package me.nawa.notification.mapper;

import java.util.List;
import me.nawa.notification.domain.PushSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 푸시 구독의 저장·조회·해지 영속성 계약이다. */
@Mapper
public interface PushSubscriptionMapper {

    // endpoint가 이미 있으면 새 행을 만들지 않고 주인과 키를 덮어쓴다. 같은 기기에 다른
    // 계정이 로그인했을 때의 소유 이전이 이 한 문장으로 끝난다.
    void upsert(
        @Param("memberId") Long memberId,
        @Param("endpoint") String endpoint,
        @Param("p256dh") String p256dh,
        @Param("authSecret") String authSecret,
        @Param("languageCode") String languageCode
    );

    // member_id로도 좁힌다. endpoint는 남이 알 수도 있는 값이라, 이 조건이 없으면 남의
    // 기기를 해지할 수 있다. 0행이어도 호출하는 쪽은 성공으로 끝낸다.
    int deleteByMemberAndEndpoint(
        @Param("memberId") Long memberId,
        @Param("endpoint") String endpoint
    );

    // 회원의 모든 기기다. 발송은 알림 하나마다 이것을 한 번 읽는다.
    List<PushSubscription> findByMemberId(@Param("memberId") Long memberId);

    // 푸시 서비스가 404·410으로 "이 구독은 끝났다"고 알릴 때 시스템이 치우는 경로다. 사용자의
    // 해지와 달리 회원으로 좁히지 않는다 — 그 주소로는 누구에게도 더는 도달하지 않기 때문이다.
    int deleteByEndpoint(@Param("endpoint") String endpoint);
}
