/**
 * 상세 행의 라벨 중 화면에 보여선 안 되는 것들.
 *
 * 수집한 영업시간은 대부분 `{ raw: '12:00 ~ 22:00' }` 한 칸짜리 객체다. `raw`는 크롤러가
 * 붙인 키 이름이라 화면 라벨이 아니고, 행에는 이미 "Hours" 같은 제목이 적혀 있다. 그대로
 * 찍으면 `raw: 12:00 ~ 22:00`이 된다. 문자열로 온 값에 우리가 붙이는 `hours`도 같은 이유로
 * 감춘다.
 *
 * **이 규칙은 값의 출처가 아니라 라벨의 성격에 대한 것이므로, 상세 행을 그리는 모든 자리가
 * 같은 목록을 써야 한다.** 예전에는 Place 상세·Event 상세·휴무일 세 곳이 각자 판단했고,
 * 세 곳의 판단이 서로 달랐다.
 *
 * - Place 상세: `raw`·`hours` 둘 다 감춤
 * - Event 상세: `raw`만 감춰서, 문자열로 온 영업시간이 `hours: ...`로 나갔다
 * - 휴무일: 아무것도 감추지 않아 `raw: ...`가 그대로 나갔다
 *
 * 마지막 것은 백엔드가 번역된 휴무일을 배열로 감싸 피해 갔는데(#531), 그 우회가 결국
 * **프론트의 렌더링 규칙이 SQL의 JSON 모양 선택을 붙잡는** 결합을 만들었다. 규칙을 한곳에
 * 두면 백엔드가 어느 모양으로 보내든 화면이 깨지지 않는다(#534).
 */
const SYNTHETIC_DETAIL_LABELS = new Set(['raw', 'hours'])

export interface LabeledEntry {
  label: string
  value: string
}

/** 라벨이 합성 키면 값만, 아니면 `라벨: 값` 한 줄로 만든다. */
export function formatDetailEntry(entry: LabeledEntry): string {
  return isSyntheticDetailLabel(entry.label) ? entry.value : `${entry.label}: ${entry.value}`
}

export function isSyntheticDetailLabel(label: string): boolean {
  return SYNTHETIC_DETAIL_LABELS.has(label.trim().toLowerCase())
}
