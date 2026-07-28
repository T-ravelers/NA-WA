# Source architecture

- `app`: 애플리케이션 부트스트랩과 전역 Provider를 구성합니다.
- `features`: 도메인 단위 기능과 해당 기능의 상태·API·컴포넌트를 구성합니다.
- `shared`: 특정 도메인에 의존하지 않는 공통 모듈을 구성합니다.

의존 방향은 `app → features → shared`를 따르며 역방향 의존을 만들지 않습니다.
