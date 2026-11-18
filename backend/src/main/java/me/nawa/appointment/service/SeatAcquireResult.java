package me.nawa.appointment.service;

/** 참여 전에 Redis에서 자리를 확인·차감한 결과다. */
public enum SeatAcquireResult {

    /** 자리 하나를 이 회원 몫으로 뺐다. DB 처리가 실패하면 돌려줘야 한다. */
    ACQUIRED,

    /** 남은 자리가 없다. DB에 가지 않고 거절한다. */
    FULL,

    /** 이미 이 회원 몫의 자리가 있다. 차감하지 않았으니 DB가 판단한다. */
    ALREADY_HOLDING,

    /** Redis를 쓸 수 없다(장애·약속 없음·이미 끝남). 입장권 없이 DB만으로 처리한다. */
    UNAVAILABLE
}
