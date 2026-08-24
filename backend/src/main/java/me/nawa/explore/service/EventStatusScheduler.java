package me.nawa.explore.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.nawa.explore.mapper.EventMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event 저장 status 정정 스케줄러
 *
 * event.status는 적재 파이프라인이 준 스냅샷이고 시간이 지나도 스스로 옮겨가지 않는다.
 * 그래서 개최가 시작된 Event가 계속 'SCHEDULED'로 남고, 아직 시작하지 않은 Event에
 * 'ENDED'가 실려 오기도 한다. 이 작업이 기준일이 정한 값으로 되돌린다.
 *
 * 화면은 이 주기를 기다리지 않는다. 목록·상세 조회가 같은 규칙을 조회 시점에 다시
 * 계산해서 내려주므로({@link EventMapper}의 eventDerivedStatusColumn) 이 작업이 늦거나
 * 멈춰도 사용자가 보는 값은 정확하다. 여기서 맞추는 것은 저장값을 그대로 읽는 쪽 —
 * 운영 조회나 나중에 붙을 집계가 화면과 다른 말을 하지 않게 하기 위해서다.
 *
 * 주기가 1분이 아니라 1시간인 것은 전이 기준이 날짜라서다. 한 행이 옮겨갈 일은 하루에
 * 많아야 한 번인데, 이 구문은 인덱스를 타지 못하고 event 전체를 훑는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventStatusScheduler {

    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    private final EventMapper eventMapper;

    @Scheduled(fixedDelay = ONE_HOUR_MILLIS)
    @Transactional
    public void realignStatuses() {
        // DB의 CURRENT_DATE() 대신 애플리케이션 날짜를 넘긴다. 조회 경로가 쓰는 기준일과
        // 같은 값이어야 저장값과 화면이 같은 날을 기준으로 갈린다.
        int moved = eventMapper.realignEventStatuses(LocalDate.now());
        if (moved > 0) {
            log.info("Realigned {} event status rows", moved);
        }
    }
}
