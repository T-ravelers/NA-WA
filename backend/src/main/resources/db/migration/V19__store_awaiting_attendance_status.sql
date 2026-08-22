-- 활동 종료 후 출석 확정 전 구간을 DB에도 기록한다.
--
-- 그동안 이 구간은 조회 응답만 계산해서 보여줬고 컬럼은 출석 확정 전까지 IN_PROGRESS에
-- 머물렀다. 그래서 컬럼으로 거르는 코드(지갑 QR 공동결제의 진행 중 약속 목록 등)가
-- 화면과 다른 말을 했다. 이제 스케줄러가 이 전이도 기록한다.

ALTER TABLE appointments
    MODIFY COLUMN appointment_status
        ENUM(
            'PAYMENT_PENDING',
            'RECRUITING',
            'FULL',
            'IN_PROGRESS',
            'AWAITING_ATTENDANCE',
            'COMPLETED',
            'CANCELLED'
        ) NOT NULL DEFAULT 'PAYMENT_PENDING';

-- 이미 활동이 끝난 채 IN_PROGRESS로 남아 있는 행은 여기서 옮기지 않는다. 옮기려면
-- activity_end_at을 DB의 NOW()와 비교해야 하는데, 그 값은 애플리케이션이 자기 시간대로
-- 저장한 것이라 두 컨테이너의 시간대 차이만큼 경계가 어긋난다(AGENTS.md). 애플리케이션
-- 시각을 넘겨받는 스케줄러가 배포 직후 첫 주기에 같은 일을 정확하게 한다.
