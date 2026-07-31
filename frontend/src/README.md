# Frontend source architecture

이 디렉터리는 프론트엔드 소스의 의존 방향을 설명합니다. 새 파일을 추가하기 전에
책임에 맞는 계층을 선택하세요.

| 계층       | 책임                                                     |
| ---------- | -------------------------------------------------------- |
| `app`      | 애플리케이션 진입점, Router와 전역 Provider 구성         |
| `features` | 도메인 단위 기능과 해당 기능의 상태, API와 컴포넌트 구성 |
| `shared`   | 특정 도메인에 의존하지 않는 공통 모듈 구성               |

의존 방향은 `app → features → shared`입니다. 역방향 의존을 만들지 마세요.

상세한 폴더 구성, 상태 소유권, Query Key, API, Router, i18n과 테스트 규칙은
[프론트엔드 개발 컨벤션](../docs/DEVELOPMENT_CONVENTION.md)을 참고하세요.
