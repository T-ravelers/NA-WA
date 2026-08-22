/**
 * 여정 초대 코드 — **표시 전용**.
 *
 * 시안(`2430:4648`)의 탑승권 stub에는 `TR-8H2K` 같은 초대 코드가 놓인다. 그런데 여정은
 * 단독 소유(`trips.member_id`)이고 백엔드에 초대·참여·멤버 경로가 하나도 없다. 즉
 * **이 코드를 받아 주는 화면도 API도 아직 없다.**
 *
 * 그래도 티켓 stub을 비워 두면 조형이 무너지므로, 여정 번호에서 사람이 읽을 수 있는
 * 문자열을 만들어 그린다. 실제로 동작하는 초대 경로는 **링크와 QR**이고 그 둘은 같은
 * 여정 주소를 가리킨다. 코드는 그 주소를 눈으로 부르는 이름일 뿐이다.
 *
 * 진짜 초대 코드가 생기면 이 파일을 지우고 서버 값을 쓴다.
 */

/** 헷갈리는 글자(I·O·0·1)를 뺀 32자. 사람이 소리 내어 읽는 것을 전제로 고른다. */
const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'

const PREFIX = 'TR'
const BODY_LENGTH = 4

/**
 * 여정 번호를 코드 본문으로 옮긴다.
 *
 * 번호를 그대로 32진수로 적으면 1번 여정이 `2`가 되어 코드처럼 보이지 않는다. 자릿수를
 * 고정하고 번호를 섞어 흩는다 — 되돌릴 필요가 없으므로 암호가 아니라 표기 규칙이다.
 */
function toBody(tripId: number): string {
  /* 32^4 = 1,048,576. 자리를 넘는 여정 번호는 앞자리부터 다시 돈다. */
  let scattered = (tripId * 2_654_435_761) % 1_048_576
  let body = ''

  for (let index = 0; index < BODY_LENGTH; index += 1) {
    body = ALPHABET[scattered % ALPHABET.length] + body
    scattered = Math.floor(scattered / ALPHABET.length)
  }

  return body
}

/** `TR-8H2K` 꼴. 여정 번호가 같으면 언제 불러도 같은 코드가 나온다. */
export function buildJourneyInviteCode(tripId: number): string {
  if (!Number.isSafeInteger(tripId) || tripId <= 0) return `${PREFIX}-????`

  return `${PREFIX}-${toBody(tripId)}`
}

/**
 * 초대 링크.
 *
 * 여정 상세 주소를 그대로 쓴다. 받는 사람이 아직 여정에 들어올 수는 없지만, 주소가
 * 따로 있으면 나중에 참여 경로가 생겼을 때 이미 뿌려진 링크가 죽는다.
 */
export function buildJourneyInviteUrl(tripId: number, origin: string): string {
  return `${origin.replace(/\/+$/, '')}/journeys/${tripId}`
}
