package me.nawa.notification.service;

import java.util.List;
import me.nawa.notification.domain.Notification;

/** 적재된 알림을 받는 회원의 기기들로 웹 푸시를 보내는 계약이다. */
public interface PushSender {

    /**
     * 알림마다 수신자의 모든 기기로 푸시를 보낸다.
     *
     * 최선 노력이다. 푸시가 꺼져 있거나, 대기열이 가득 찼거나, 보내다 실패해도 예외를 던지지
     * 않고 다시 시도하지도 않는다. 호출하는 쪽의 정산·알림 처리가 이 결과에 흔들리면 안 되고,
     * 놓친 알림은 목록 폴링이 메운다. 실제 발송은 호출한 스레드가 아니라 전용 스레드풀에서 한다.
     */
    void send(List<Notification> notifications);
}
