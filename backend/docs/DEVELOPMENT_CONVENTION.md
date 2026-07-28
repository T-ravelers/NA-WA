# T-ravelers Backend 개발 컨벤션

T-ravelers Backend는 Java 17, Spring Legacy, MyBatis, MySQL, JWT 기반으로 개발합니다.  
개발 시 안정성, 보안성, 유지보수성을 우선합니다.

---

## 1. 기본 원칙

- 하나의 클래스는 하나의 책임만 가집니다.
- Controller, Service, Mapper의 역할을 명확히 분리합니다.
- 민감정보, 개인정보, 토큰, 비밀번호는 코드와 로그에 남기지 않습니다.
- 서버 내부 예외 메시지를 그대로 API 응답으로 노출하지 않습니다.
- 운영 환경에 영향을 주는 설정값은 환경 변수 또는 외부 설정으로 관리합니다.

---

## 2. Java 코드 규칙

- 들여쓰기는 공백 4칸을 사용합니다.
- 클래스명은 PascalCase를 사용합니다.
- 메서드명과 변수명은 camelCase를 사용합니다.
- 상수는 UPPER_SNAKE_CASE를 사용합니다.
- 필드는 `private final`을 기본으로 사용합니다.
- 의존성 주입은 생성자 주입을 사용합니다.
- 불필요한 주석은 작성하지 않습니다.
- `printStackTrace()`를 사용하지 않습니다.

```java

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
}
```

---

## 3. 네이밍 규칙

이름만 보고 역할과 의미를 알 수 있도록 작성합니다.

| 대상      | 규칙                     | 예시                                            |
|---------|------------------------|-----------------------------------------------|
| 클래스     | 명사 또는 명사구, PascalCase  | `MemberService`, `TravelPlanController`       |
| 메서드     | 동사 또는 동사구, camelCase   | `createMember`, `findTravelPlanById`          |
| 변수/필드   | 의미가 드러나는 명사, camelCase | `memberId`, `travelPlanList`                  |
| 상수      | UPPER_SNAKE_CASE       | `MAX_LOGIN_ATTEMPT`                           |
| 테스트 메서드 | 대상_상황_결과               | `createMember_duplicateEmail_throwsException` |

피해야 할 이름입니다.

```text
data
tmp
obj
process()
handle()
doSomething()
```

---

## 4. 메서드명 규칙

메서드명은 동사 + 명사 형태로 작성합니다.  
메서드명만 보고 수행하는 동작과 대상이 드러나야 합니다.

| 상황    | 권장 동사                         | 예시                     |
|-------|-------------------------------|------------------------|
| 단건 조회 | `find`, `get`                 | `findMemberById`       |
| 목록 검색 | `search`, `find`              | `searchTravelPlans`    |
| 생성    | `create`, `save`, `register`  | `createTravelPlan`     |
| 수정    | `update`, `change`            | `updateMemberPassword` |
| 검증    | `validate`, `check`, `exists` | `existsMemberByEmail`  |

Controller 메서드는 HTTP 행위가 드러나도록 작성합니다.

```java
getMember()

createMember()

updateMember()

deleteMember()
```

MyBatis Mapper 메서드는 SQL 목적이 드러나도록 작성합니다.

```java
findById()

insertMember()

updateMember()

deleteById()

existsByEmail()
```

---

## 5. REST API 규칙

URL은 리소스 중심으로 작성하고, 동작은 HTTP Method로 표현합니다.

| 상황       | Method | URL                       |
|----------|--------|---------------------------|
| 회원 단건 조회 | GET    | `/api/members/{memberId}` |
| 회원 생성    | POST   | `/api/members`            |
| 회원 수정    | PUT    | `/api/members/{memberId}` |
| 회원 삭제    | DELETE | `/api/members/{memberId}` |

동사는 URL에 넣지 않습니다.

| 좋은 예                          | 피해야 할 예                      |
|-------------------------------|------------------------------|
| `POST /api/travel-plans`      | `POST /api/createTravelPlan` |
| `GET /api/members/{memberId}` | `GET /api/getMember`         |

응답 상태 코드는 의미에 맞게 사용합니다.

| 상태 코드                     | 의미       |
|---------------------------|----------|
| 200 OK                    | 요청 성공    |
| 201 Created               | 생성 성공    |
| 400 Bad Request           | 잘못된 요청   |
| 401 Unauthorized          | 인증 실패    |
| 403 Forbidden             | 권한 없음    |
| 404 Not Found             | 리소스 없음   |
| 409 Conflict              | 중복 또는 충돌 |
| 500 Internal Server Error | 서버 오류    |

---

## 6. DTO 규칙

- Controller는 Entity 또는 VO를 직접 받거나 반환하지 않습니다.
- 요청 DTO와 응답 DTO를 분리합니다.
- Request DTO에는 입력 검증 기준을 명확히 둡니다.
- Response DTO에는 클라이언트에 필요한 값만 포함합니다.

| 상황    | 이름 예시                 |
|-------|-----------------------|
| 생성 요청 | `MemberCreateRequest` |
| 수정 요청 | `MemberUpdateRequest` |
| 응답    | `MemberResponse`      |

---

## 7. Service 규칙

- 비즈니스 로직은 Service에 작성합니다.
- Controller에는 요청 검증, Service 호출, 응답 반환만 둡니다.
- DB 변경 작업은 `@Transactional`을 사용합니다.
- 조회 전용 메서드는 `@Transactional(readOnly = true)` 사용을 권장합니다.
- Controller나 Mapper에는 트랜잭션을 직접 선언하지 않습니다.
- 비밀번호, 토큰, 권한 관련 로직은 명확한 검증 절차를 둡니다.

| 상황                            | 작성 방식                                           |
|-------------------------------|-------------------------------------------------|
| DB 변경 작업                      | `@Transactional`                                |
| 조회 전용 작업                      | `@Transactional(readOnly = true)`               |
| RuntimeException rollback     | 기본 rollback                                     |
| checked exception rollback 필요 | `@Transactional(rollbackFor = Exception.class)` |

```java

@Transactional
public MemberResponse createMember(MemberCreateRequest request) {
    // 회원 생성 로직
}
```

---

## 8. MyBatis 규칙

- Mapper 인터페이스와 XML namespace는 반드시 일치시킵니다.
- SQL id는 Mapper 메서드명과 일치시킵니다.
- 사용자 입력값은 문자열 결합이 아니라 바인딩 파라미터로 처리합니다.
- 사용자 입력값에는 `${}`를 사용하지 않고 `#{}`를 사용합니다.
- 복잡한 조회는 resultMap을 사용합니다.
- 동적 SQL은 필요한 경우에만 사용합니다.

| 상황           | 작성 방식               |
|--------------|---------------------|
| 일반 파라미터 바인딩  | `#{memberId}`       |
| 사용자 입력값 처리   | `#{keyword}`        |
| SQL 조각 직접 삽입 | `${}` 사용 금지         |
| 정렬 컬럼 동적 처리  | 허용 목록 검증 후 제한적으로 사용 |
| 복잡한 결과 매핑    | `resultMap` 사용      |

```xml

<mapper namespace="me.nawa.member.mapper.MemberMapper">
    <select id="findById" parameterType="long" resultType="Member">
        SELECT *
        FROM members
        WHERE member_id = #{memberId}
    </select>
</mapper>
```

---

## 9. DB 규칙

- 테이블명과 컬럼명은 snake_case를 사용합니다.
- 기본키는 의미가 드러나도록 작성합니다.
- 생성일, 수정일 컬럼을 가능한 한 포함합니다.
- 금액, 권한, 상태값처럼 중요한 데이터는 의미가 명확한 이름을 사용합니다.

| 상황       | 이름 예시            |
|----------|------------------|
| 회원 PK    | `member_id`      |
| 여행 일정 PK | `travel_plan_id` |
| 생성일      | `created_at`     |
| 수정일      | `updated_at`     |

---

## 10. 예외 처리 규칙

- 공통 예외는 전역 예외 처리에서 관리합니다.
- 서버 내부 예외 메시지를 그대로 응답하지 않습니다.
- 클라이언트에는 정해진 오류 형식으로 응답합니다.
- 인증, 권한, 입력 오류, 데이터 없음, 중복 요청은 구분해서 처리합니다.

```json
{
  "code": "MEMBER_NOT_FOUND",
  "message": "회원을 찾을 수 없습니다."
}
```

---

## 11. 보안 규칙

- 비밀번호는 반드시 단방향 암호화 후 저장합니다.
- JWT Secret, DB Password, AWS Key는 코드에 작성하지 않습니다.
- Access Token과 Refresh Token의 역할을 구분합니다.
- 인증이 필요한 API는 Security 설정에서 명확히 보호합니다.
- 개인정보, 비밀번호, JWT, 인증 헤더는 로그에 남기지 않습니다.

---

## 12. Redis 규칙

- Redis Key는 기능과 목적이 드러나도록 작성합니다.
- TTL이 필요한 데이터는 반드시 만료 시간을 설정합니다.
- 인증, 토큰, 임시 데이터 저장 시 Key 충돌을 방지합니다.

| 상황              | Key 예시                          |
|-----------------|---------------------------------|
| Refresh Token   | `auth:refresh-token:{memberId}` |
| Token Blacklist | `auth:blacklist:{tokenId}`      |
| 인증번호            | `auth:code:{purpose}:{target}`  |

---

## 13. Docker Compose와 AWS 규칙

- Docker Compose는 로컬 개발 환경 구성에 사용합니다.
- DB, Redis 등 외부 의존성을 명확히 분리합니다.
- 운영 설정과 로컬 개발 설정을 혼동하지 않습니다.
- AWS Access Key와 Secret Key는 코드와 Git에 올리지 않습니다.
- S3, RDS, EC2 등 리소스명은 환경을 구분할 수 있게 작성합니다.
- 업로드 파일 경로는 로컬 하드코딩 경로를 사용하지 않습니다.

---

## 14. 테스트 규칙

- 핵심 비즈니스 로직은 단위 테스트를 작성합니다.
- Mapper 또는 DB 연동이 필요한 로직은 통합 테스트로 검증합니다.
- 인증, 권한, 결제, 개인정보 관련 로직은 정상 케이스와 실패 케이스를 함께 검증합니다.
- PR에는 수행한 테스트 결과를 작성합니다.

| 상황       | 테스트명 예시                                        |
|----------|------------------------------------------------|
| 정상 생성    | `createMember_success`                         |
| 중복 이메일   | `createMember_duplicateEmail_throwsException`  |
| 조회 실패    | `findMemberById_notFound_throwsException`      |
| 비밀번호 불일치 | `updatePassword_wrongPassword_throwsException` |

---

## 15. 로그 규칙

- 로그는 문제 추적에 필요한 정보만 남깁니다.
- 이름, 이메일, 전화번호 대신 내부 식별자를 우선 사용합니다.
- Access Token, Refresh Token, Session ID, Authorization Header는 로그에 남기지 않습니다.
- DB Connection String, AWS Key, JWT Secret은 로그에 남기지 않습니다.
- 예외는 `log.error()`로 기록합니다.
- 단순 확인용 로그는 PR 전에 제거합니다.

```java
log.error("회원 조회 중 오류가 발생했습니다. memberId={}",memberId, e);
```

---

## 16. Frontend 연동 규칙

Vue.js와 연동되는 API는 요청/응답 형식을 명확히 유지합니다.

- 응답 필드명은 camelCase를 사용합니다.
- 오류 응답 형식은 일관되게 유지합니다.
- API 변경 시 프론트엔드 영향 여부를 PR에 작성합니다.
- Tailwind CSS 클래스나 화면 구조에 의존하는 값을 Backend에서 만들지 않습니다.

---

## 17. 금지 사항

- DB 비밀번호, JWT Secret, AWS Key 하드코딩
- `printStackTrace()` 사용
- Controller에 비즈니스 로직 작성
- SQL 문자열 직접 결합
- MyBatis에서 사용자 입력값에 `${}` 사용
- 운영 환경 설정값을 로컬 설정 파일에 직접 작성
- 개인정보, 비밀번호, 토큰 로그 출력
- 의미 없는 Commit 메시지 작성

```text
나쁜 예:
수정
작업 완료
코드 변경
```