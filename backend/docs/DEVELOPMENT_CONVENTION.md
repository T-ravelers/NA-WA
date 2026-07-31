# NA-WA Backend 개발 컨벤션

이 문서는 Java 17, Spring MVC, MyBatis, MySQL과 JWT를 사용하는 NA-WA 백엔드의 구현
규칙을 정의합니다. 새 API나 도메인을 만들 때 계층 책임, 이름, 데이터 접근, 보안과
검증 기준을 결정하는 데 사용하세요.

API 성공·실패 응답과 오류 코드는
[API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 함께 따릅니다.

## 기본 원칙

- 클래스마다 하나의 주요 책임을 둡니다.
- Controller, Service와 Mapper의 역할을 구분합니다.
- 민감정보, 개인정보, 토큰과 비밀번호를 코드나 로그에 남기지 않습니다.
- 서버 내부 예외 메시지를 API 응답에 그대로 노출하지 않습니다.
- 운영 설정은 환경 변수 또는 외부 설정으로 주입합니다.

## 계층 책임 구분하기

| 계층       | 책임                                     |
| ---------- | ---------------------------------------- |
| Controller | HTTP 요청 검증, Service 호출과 응답 반환 |
| Service    | 비즈니스 규칙, 권한 판단과 트랜잭션      |
| Mapper     | SQL 실행과 데이터 매핑                   |

Controller에 비즈니스 규칙을 작성하지 마세요. Mapper가 반환한 결과의 의미는
Service에서 판단하세요.

## Java 코드 작성하기

- 공백 4칸으로 들여씁니다.
- 클래스명은 PascalCase를 사용합니다.
- 메서드명과 변수명은 camelCase를 사용합니다.
- 상수는 UPPER_SNAKE_CASE를 사용합니다.
- 필드는 `private final`을 기본으로 선언합니다.
- 의존성은 생성자로 주입합니다.
- 코드가 설명하는 내용을 반복하는 주석을 작성하지 않습니다.
- `printStackTrace()`를 사용하지 않습니다.

```java
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
}
```

## 이름 정하기

이름만 읽어도 역할과 의미를 알 수 있어야 합니다.

| 대상          | 규칙                            | 예시                                          |
| ------------- | ------------------------------- | --------------------------------------------- |
| 클래스        | 명사 또는 명사구, PascalCase    | `MemberService`, `JourneyController`          |
| 메서드        | 동사 또는 동사구, camelCase     | `createMember`, `findJourneyById`             |
| 변수와 필드   | 의미가 드러나는 명사, camelCase | `memberId`, `journeyList`                     |
| 상수          | UPPER_SNAKE_CASE                | `MAX_LOGIN_ATTEMPT`                           |
| 테스트 메서드 | 대상_상황_결과                  | `createMember_duplicateEmail_throwsException` |

`data`, `tmp`, `obj`, `process()`, `handle()`, `doSomething()`처럼 의미를 알 수 없는
이름을 사용하지 마세요.

### 메서드 이름

| 동작      | 권장 동사                     | 예시                   |
| --------- | ----------------------------- | ---------------------- |
| 단건 조회 | `find`, `get`                 | `findMemberById`       |
| 목록 검색 | `search`, `find`              | `searchJourneys`       |
| 생성      | `create`, `save`, `register`  | `createJourney`        |
| 수정      | `update`, `change`            | `updateMemberPassword` |
| 검증      | `validate`, `check`, `exists` | `existsMemberByEmail`  |

Controller 메서드에는 HTTP 행위와 대상이 드러나는 이름을 사용하세요.

```java
getMember()
createMember()
updateMember()
deleteMember()
```

Mapper 메서드에는 SQL 목적이 드러나는 이름을 사용하세요.

```java
findById()
insertMember()
updateMember()
deleteById()
existsByEmail()
```

## REST API 설계하기

URL에는 리소스를 표현하고 동작은 HTTP Method로 구분합니다.

| 동작           | Method | URL                       |
| -------------- | ------ | ------------------------- |
| 회원 단건 조회 | GET    | `/api/members/{memberId}` |
| 회원 생성      | POST   | `/api/members`            |
| 회원 수정      | PUT    | `/api/members/{memberId}` |
| 회원 삭제      | DELETE | `/api/members/{memberId}` |

URL에 `create`, `get`과 같은 동사를 넣지 마세요.

| 권장                          | 사용하지 않음             |
| ----------------------------- | ------------------------- |
| `POST /api/journeys`          | `POST /api/createJourney` |
| `GET /api/members/{memberId}` | `GET /api/getMember`      |

HTTP 상태와 응답 본문은
[API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)에 정의된 계약을
사용하세요.

## DTO 분리하기

- Controller에서 Entity 또는 VO를 직접 받거나 반환하지 않습니다.
- 요청 DTO와 응답 DTO를 분리합니다.
- Request DTO에 입력 검증 조건을 정의합니다.
- Response DTO에는 클라이언트에 필요한 값만 포함합니다.

| 용도      | 이름 예시             |
| --------- | --------------------- |
| 생성 요청 | `MemberCreateRequest` |
| 수정 요청 | `MemberUpdateRequest` |
| 응답      | `MemberResponse`      |

## Service와 트랜잭션 관리하기

- 비즈니스 규칙을 Service에 작성합니다.
- DB를 변경하는 작업에는 `@Transactional`을 사용합니다.
- 조회 전용 작업에는 `@Transactional(readOnly = true)` 사용을 우선 검토합니다.
- Controller와 Mapper에 트랜잭션을 선언하지 않습니다.
- 비밀번호, 토큰과 권한을 다루는 로직에는 명시적인 검증 절차를 둡니다.

| 상황                            | 작성 방식                                       |
| ------------------------------- | ----------------------------------------------- |
| DB 변경                         | `@Transactional`                                |
| 조회 전용                       | `@Transactional(readOnly = true)`               |
| RuntimeException rollback       | Spring 기본 rollback                            |
| checked exception rollback 필요 | `@Transactional(rollbackFor = Exception.class)` |

```java
@Transactional
public MemberResponse createMember(MemberCreateRequest request) {
    // 회원 생성 로직
}
```

## MyBatis 사용하기

- Mapper 인터페이스와 XML namespace를 일치시킵니다.
- SQL id와 Mapper 메서드명을 일치시킵니다.
- 사용자 입력은 문자열 결합 대신 `#{}` 바인딩 파라미터로 처리합니다.
- 사용자 입력에 `${}`를 사용하지 않습니다.
- 복잡한 결과 매핑에는 `resultMap`을 사용합니다.
- 동적 SQL은 조건에 따라 쿼리가 실제로 달라질 때만 사용합니다.

| 상황               | 작성 방식                             |
| ------------------ | ------------------------------------- |
| 일반 파라미터      | `#{memberId}`                         |
| 사용자 입력        | `#{keyword}`                          |
| SQL 조각 직접 삽입 | `${}` 사용 금지                       |
| 동적 정렬 컬럼     | 허용 목록을 검증한 뒤 제한적으로 사용 |
| 복잡한 결과 매핑   | `resultMap`                           |

```xml
<mapper namespace="me.nawa.member.mapper.MemberMapper">
    <select id="findById" parameterType="long" resultType="Member">
        SELECT *
        FROM members
        WHERE member_id = #{memberId}
    </select>
</mapper>
```

## 데이터베이스 이름 정하기

- 테이블명과 컬럼명은 snake_case를 사용합니다.
- 기본키 이름에 대상 리소스를 포함합니다.
- 생성일과 수정일 컬럼을 필요한 테이블에 포함합니다.
- 금액, 권한과 상태값에는 의미가 드러나는 이름을 사용합니다.

| 용도    | 이름 예시    |
| ------- | ------------ |
| 회원 PK | `member_id`  |
| 여행 PK | `journey_id` |
| 생성일  | `created_at` |
| 수정일  | `updated_at` |

## 예외 처리하기

- REST API 예외는 `@RestControllerAdvice` 기반 전역 처리기에서 공통 형식으로
  변환합니다.
- Service에서 도메인 오류를 판단하고 `BusinessException`을 발생시킵니다.
- Controller에서 비즈니스 예외를 반복해서 `try-catch`하지 않습니다.
- 서버 내부 예외 메시지를 응답에 포함하지 않습니다.
- 인증, 권한, 입력 오류, 데이터 없음과 충돌을 서로 다른 오류 코드로 구분합니다.

```json
{
  "success": false,
  "error": {
    "code": "MEMBER-001",
    "message": "회원을 찾을 수 없습니다."
  }
}
```

오류 코드 작성 위치와 처리 흐름은
[API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 참고하세요.

## 인증과 보안 지키기

- 비밀번호는 단방향 해시로 저장합니다.
- JWT Secret, DB Password와 AWS Key를 코드에 작성하지 않습니다.
- Access Token과 Refresh Token의 역할과 수명을 구분합니다.
- 인증이 필요한 API를 Spring Security 설정에서 명시적으로 보호합니다.
- 개인정보, 비밀번호, JWT와 Authorization Header를 로그에 남기지 않습니다.

## Redis Key 설계하기

- Key에서 기능과 목적을 알 수 있어야 합니다.
- 수명이 있는 데이터에는 TTL을 설정합니다.
- 인증, 토큰과 임시 데이터의 Key namespace를 분리합니다.

| 데이터          | Key 예시                       |
| --------------- | ------------------------------ |
| Refresh Token   | `auth:refresh:{sessionId}`     |
| Token Blacklist | `auth:blacklist:{tokenId}`     |
| 인증번호        | `auth:code:{purpose}:{target}` |

인증 상태와 일반 캐시가 같은 Redis를 사용한다면 eviction이 인증에 미치는 영향을
검토하세요.

## Docker Compose와 AWS 설정하기

- Docker Compose는 로컬 개발과 배포에 필요한 서비스를 구분해 구성합니다.
- DB와 Redis 등 외부 의존성을 명시합니다.
- 운영 설정과 로컬 개발 설정을 섞지 않습니다.
- AWS Access Key와 Secret Key를 코드와 Git에 저장하지 않습니다.
- S3, RDS와 EC2 리소스명에서 환경을 구분할 수 있어야 합니다.
- 업로드 파일 경로에 로컬 절대 경로를 하드코딩하지 않습니다.

## 테스트 작성하기

- 핵심 비즈니스 규칙은 단위 테스트로 검증합니다.
- Mapper 또는 DB 연동이 필요한 동작은 통합 테스트로 검증합니다.
- 인증, 권한, 결제와 개인정보 관련 로직은 성공과 실패를 함께 검증합니다.
- 버그 수정에는 가능하면 실패를 재현하는 회귀 테스트를 추가합니다.
- PR에 실행한 테스트와 결과를 작성합니다.

| 상황            | 테스트명 예시                                  |
| --------------- | ---------------------------------------------- |
| 정상 생성       | `createMember_success`                         |
| 중복 이메일     | `createMember_duplicateEmail_throwsException`  |
| 조회 실패       | `findMemberById_notFound_throwsException`      |
| 비밀번호 불일치 | `updatePassword_wrongPassword_throwsException` |

## 로그 남기기

- 문제 추적에 필요한 정보만 기록합니다.
- 이름, 이메일과 전화번호보다 내부 식별자를 사용합니다.
- Access Token, Refresh Token, Session ID와 Authorization Header를 기록하지
  않습니다.
- DB Connection String, AWS Key와 JWT Secret을 기록하지 않습니다.
- 예상하지 못한 예외는 stack trace와 함께 `log.error()`로 기록합니다.
- 확인용 로그는 PR을 열기 전에 제거합니다.

```java
log.error("회원 조회 중 오류가 발생했습니다. memberId={}", memberId, e);
```

## 프론트엔드와 API 계약 변경하기

- API 응답 필드명은 camelCase를 사용합니다.
- 프론트엔드는 오류 메시지가 아니라 오류 코드를 기준으로 분기합니다.
- 응답 필드, HTTP 상태 또는 오류 코드를 변경하면 PR에 프론트엔드 영향을
  작성합니다.
- Tailwind CSS 클래스나 화면 구조에 의존하는 값을 백엔드에서 만들지 않습니다.

API 계약을 변경할 때는
[API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 먼저 수정하고
프론트엔드와 함께 검토하세요.

## 사용하지 않는 구현

- DB 비밀번호, JWT Secret 또는 AWS Key 하드코딩
- `printStackTrace()` 호출
- Controller의 비즈니스 로직
- SQL 문자열 직접 결합
- MyBatis 사용자 입력에 `${}` 사용
- 운영 설정을 로컬 설정 파일에 직접 작성
- 개인정보, 비밀번호 또는 토큰 로그
- `수정`, `작업 완료`, `코드 변경`처럼 의미를 알 수 없는 커밋 메시지
