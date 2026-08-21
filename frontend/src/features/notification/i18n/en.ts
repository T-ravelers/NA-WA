/**
 * 알림 화면 문구.
 *
 * 정산 도메인의 UI 어휘는 `settlement`이 아니라 `split`이다. 코드와 API는 `settlement`을
 * 그대로 쓰지만 사용자에게 보이는 말은 `split`으로 맞춘다.
 *
 * 로딩·빈 상태·일반 오류는 `shared/i18n`을 쓰고 여기에 다시 만들지 않는다.
 */
export default {
  notification: {
    title: 'Notifications',
    bell: 'Notifications',
    /** 배지에 읽히는 이름. 숫자만으로는 무엇의 개수인지 알 수 없다. */
    unreadBadge: '{count} unread',
    unreadBadgeOverflow: '9+',
    /**
     * 목록에서 안 읽은 항목 앞에 눈에 보이지 않게 붙는 말.
     *
     * 안 읽음을 점 하나로만 말하면 화면을 못 보는 사람에게는 아무 말도 하지 않은 것과
     * 같다. 배지의 `{count} unread`와 짝을 이룬다.
     */
    unread: 'Unread',
    empty: {
      title: 'No notifications yet',
      description: 'Split requests and payments will show up here.',
    },
    item: {
      SETTLEMENT_REQUESTED: '{actor} asked you for {amount} for {gathering}',
      SETTLEMENT_PAID: '{actor} paid you {amount} for {gathering}',
      SETTLEMENT_COMPLETED: 'Everyone has paid for {gathering}',
      UNKNOWN: 'Something happened with {gathering}',
    },
  },
}
