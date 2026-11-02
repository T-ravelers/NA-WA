/**
 * 목록 카드가 다른 feature 화면에 들렀다 돌아올 때 주소에 싣는 표시.
 *
 * 탐색 진입 가드(`routes.ts`)와 목록 화면이 같은 이름을 읽어야 해서 한곳에 둔다. 받는 쪽
 * feature는 import할 수 없으므로 같은 글자를 따로 쓴다(약속 목록의 `fromExplore`).
 */

/** 여정을 만들러 나갈 때 어느 카드를 담던 중이었는지. 목록은 카드가 여럿이라 param으로 알 수 없다. */
export const JOURNEY_ADD_ITEM_ID_KEY = 'journeyAddItemId'
export const JOURNEY_ADD_ITEM_TYPE_KEY = 'journeyAddItemType'

/**
 * 카드에서 동행 찾기로 연 약속 목록에 다는 표시.
 *
 * 공용 복귀 키(`returnRouteName`)를 쓰지 않는다. 약속 목록이 참여 흐름에서 여정 생성·충전을
 * 다녀올 때 그 키를 자기 이름으로 덮어쓰고, 돌아오면 지운다.
 */
export const FROM_EXPLORE_KEY = 'fromExplore'

/** 카드에서 연 약속 목록을 떠나기 전 목록 스크롤 위치. 라우터에 스크롤 복원이 없다. */
export const LIST_SCROLL_STORAGE_KEY = 'nawa.explore.listScrollBeforeBuddy'
