package me.nawa.notification.service;

/**
 * 리마인더 실행 한 번의 요약이다. 실행 이력 표를 두지 않고 이 값을 로그와 반환값으로 남긴다.
 *
 * @param chunks        후보를 읽어 처리한 묶음 수
 * @param reminded      알림을 적은 사람 수
 * @param skipped       묶음 실패 뒤 한 건씩 다시 시도해도 실패해서 건너뛴 사람 수. 이 사람들은
 *                      발송 기록이 없으므로 다음 실행이 다시 시도한다.
 * @param elapsedMillis 실행에 걸린 시간
 */
public record SettlementReminderResult(int chunks, int reminded, int skipped, long elapsedMillis) {
}
