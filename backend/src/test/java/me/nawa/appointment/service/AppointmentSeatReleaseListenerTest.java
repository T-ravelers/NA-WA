package me.nawa.appointment.service;

import me.nawa.appointment.event.AppointmentMemberLeftEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * 참여 취소의 자리 반환이 트랜잭션 결과를 따르는지 본다(#612). 실제 커밋·롤백 순서가 핵심이라
 * DB 없이 트랜잭션만 흉내 내는 작은 Spring 환경에서 돌린다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppointmentSeatReleaseListenerTest.Config.class)
class AppointmentSeatReleaseListenerTest {

    private static final AppointmentMemberLeftEvent LEFT = new AppointmentMemberLeftEvent(101L, 7L);

    @Autowired
    private AppointmentSeatGate seatGate;

    @Autowired
    private Leaving leaving;

    @Autowired
    private OuterWork outerWork;

    @BeforeEach
    void resetGate() {
        reset(seatGate);
    }

    @Test
    void releasesTheSeat_afterCommit() {
        leaving.leave();

        verify(seatGate).release(101L, 7L);
    }

    @Test
    void keepsTheSeat_whenTheLeaveRollsBack() {
        assertThrows(IllegalStateException.class, () -> leaving.leaveThenFail());

        verify(seatGate, never()).release(any(), any());
    }

    /** 여정에서 약속 항목을 지울 때처럼 바깥 트랜잭션 안에서 나가면 바깥이 커밋돼야 돌려준다. */
    @Test
    void releasesTheSeat_onlyWhenTheOuterTransactionCommits() {
        outerWork.leaveInside();
        verify(seatGate).release(101L, 7L);

        reset(seatGate);
        assertThrows(IllegalStateException.class, () -> outerWork.leaveInsideThenFail());
        verify(seatGate, never()).release(any(), any());
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        AppointmentSeatGate seatGate() {
            return mock(AppointmentSeatGate.class);
        }

        @Bean
        AppointmentSeatReleaseListener listener(AppointmentSeatGate seatGate) {
            return new AppointmentSeatReleaseListener(seatGate);
        }

        @Bean
        FakeTransactionManager transactionManager() {
            return new FakeTransactionManager();
        }

        @Bean
        Leaving leaving(ApplicationEventPublisher publisher) {
            return new Leaving(publisher);
        }

        @Bean
        OuterWork outerWork(Leaving leaving) {
            return new OuterWork(leaving);
        }
    }

    /** {@code leaveAppointment}처럼 트랜잭션 안에서 나갔다는 이벤트를 발행한다. */
    static class Leaving {
        private final ApplicationEventPublisher publisher;

        Leaving(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void leave() {
            publisher.publishEvent(LEFT);
        }

        @Transactional
        public void leaveThenFail() {
            publisher.publishEvent(LEFT);
            throw new IllegalStateException("rollback");
        }
    }

    /** {@code JourneyService}처럼 자기 트랜잭션 안에서 나가기를 부른다. */
    static class OuterWork {
        private final Leaving leaving;

        OuterWork(Leaving leaving) {
            this.leaving = leaving;
        }

        @Transactional
        public void leaveInside() {
            leaving.leave();
        }

        @Transactional
        public void leaveInsideThenFail() {
            leaving.leave();
            throw new IllegalStateException("outer rollback");
        }
    }

    /** 자원 없이 커밋·롤백 순서와 트랜잭션 동기화만 흉내 낸다. */
    static class FakeTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        }
    }
}
