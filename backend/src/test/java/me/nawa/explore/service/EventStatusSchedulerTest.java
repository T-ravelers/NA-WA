package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import me.nawa.explore.mapper.EventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventStatusSchedulerTest {

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventStatusScheduler scheduler;

    /*
     * 기준일은 애플리케이션이 정한다. DB의 CURRENT_DATE()로 갈리면 컨테이너 시간대에
     * 따라 저장값과 조회 응답이 서로 다른 날을 기준으로 움직인다.
     */
    @Test
    void realignStatuses_passesApplicationDateToMapper() {
        when(eventMapper.realignEventStatuses(any(LocalDate.class)))
            .thenReturn(0);

        scheduler.realignStatuses();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(
            LocalDate.class
        );
        verify(eventMapper).realignEventStatuses(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue());
    }
}
