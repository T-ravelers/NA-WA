# NA-WA Backend

NA-WA 백엔드는 Java 17과 Spring MVC로 구성한 WAR 애플리케이션입니다. 이 문서에서
백엔드 개발 규칙, API 응답 계약과 기본 검증 방법을 찾을 수 있습니다.

## 필요한 문서 찾기

| 하려는 일                              | 문서                                                            |
| -------------------------------------- | --------------------------------------------------------------- |
| Issue, 브랜치, 커밋과 PR 규칙 확인     | [공통 협업 가이드](../CONTRIBUTING.md)                          |
| Java, Spring, MyBatis와 보안 규칙 확인 | [백엔드 개발 컨벤션](docs/DEVELOPMENT_CONVENTION.md)            |
| API 성공·실패 응답 계약 확인           | [API 응답 및 오류 코드 컨벤션](docs/API_RESPONSE_CONVENTION.md) |
| 프로젝트 전체 기술 경계 확인           | [기술 스택과 운영 경계](../docs/TECH_STACK.md)                  |

## 변경 사항 검증

백엔드 디렉터리에서 Gradle 빌드와 테스트를 실행하세요.

```shell
cd backend
./gradlew build --no-daemon
```

API 계약을 변경했다면 단위·통합 테스트와 Swagger 또는 Postman 확인 결과를 PR에
작성하세요.

## API 문서 확인

API별 요청 파라미터와 응답 데이터 구조는 Swagger에서 확인할 수 있습니다.

- 로컬 Swagger UI: `http://localhost:{port}/swagger-ui/index.html`
