package me.nawa.explore.service;

import lombok.RequiredArgsConstructor;
import me.nawa.explore.mapper.EventMapper;
import me.nawa.explore.mapper.PlaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상세 조회수를 쌓는다.
 *
 * 상세 조회는 읽기 전용 트랜잭션이라 그 안에서 UPDATE를 할 수 없다. 그래서 별도 빈으로
 * 두고 {@link Propagation#REQUIRES_NEW}로 자기 트랜잭션을 연다. 같은 클래스 안의 메서드로
 * 두면 프록시를 타지 않아 전파 설정이 무시된다.
 *
 * 실패해도 상세 응답은 나가야 하므로 예외는 호출부가 삼킨다. 여기서 삼키면 이미
 * 롤백 표시가 붙은 트랜잭션을 커밋하려다 다시 터진다.
 */
@Service
@RequiredArgsConstructor
public class ExploreViewCountRecorder {

    private final EventMapper eventMapper;
    private final PlaceMapper placeMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEventView(Long eventId) {
        eventMapper.increaseEventViewCount(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPlaceView(Long placeId) {
        placeMapper.increasePlaceViewCount(placeId);
    }
}
