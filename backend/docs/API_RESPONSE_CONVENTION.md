# NA-WA API 공통 응답 및 오류 코드 컨벤션

## 1. 목적

NA-WA 백엔드 API의 성공 및 실패 응답 형식을 통일하고, 모든 도메인이 같은 오류 코드 규격과 예외 처리 방식을 사용하도록 합니다.

프론트엔드는 공통 응답 구조를 기준으로 API 요청 결과를 처리하고, 백엔드의 각 도메인 담당자는 공통 `ErrorCode` 인터페이스를 구현해 자신의 도메인 오류 코드를 작성합니다.

---

## 2. 공통 원칙

- 일반 JSON API는 `ApiResponse`를 사용합니다.
- API 응답 필드명은 camelCase를 사용합니다.
- HTTP 상태 코드는 실제 요청 처리 결과에 맞게 반환합니다.
- 성공 여부는 `success` 필드로 구분합니다.
- 성공 응답에는 `data`를 사용합니다.
- 실패 응답에는 `error`를 사용합니다.
- 프론트엔드는 오류 메시지가 아니라 `error.code`를 기준으로 오류를 처리합니다.
- `error.message`에는 사용자에게 보여줄 수 있는 안전한 안내 문구만 작성합니다.
- 서버 내부 예외 메시지, 스택 트레이스, SQL, 개인정보, 인증 정보는 응답에 포함하지 않습니다.
- 값이 없는 `data`와 `error`는 JSON 응답에서 제외합니다.

---

## 3. 성공 응답

### 3.1 반환 데이터가 있는 성공 응답

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "nickname": "여행자"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `success` | boolean | O | 성공 여부이며 항상 `true`입니다. |
| `data` | object 또는 array | O | API별 응답 데이터를 포함합니다. |

Controller에서는 다음과 같이 생성합니다.

```java
MemberResponse response = memberService.getMember(memberId);

return ApiResponse.success(response);
```

### 3.2 반환 데이터가 없는 200 성공 응답

반환할 데이터가 없지만 성공 응답 본문이 필요한 경우 다음과 같이 반환합니다.

```json
{
  "success": true
}
```

Controller에서는 다음과 같이 생성합니다.

```java
return ApiResponse.success();
```

`ApiResponse`는 null 필드를 JSON에서 제외하므로 `data`는 응답에 포함되지 않습니다.

### 3.3 204 No Content

삭제 성공처럼 응답 본문이 필요하지 않은 경우 `204 No Content`를 사용할 수 있습니다.

`204 No Content` 응답에는 `ApiResponse`를 사용하지 않으며 응답 본문을 반환하지 않습니다.

---

## 4. 실패 응답

모든 일반 API 실패 응답은 다음 구조를 사용합니다.

```json
{
  "success": false,
  "error": {
    "code": "MEMBER-001",
    "message": "회원을 찾을 수 없습니다."
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `success` | boolean | O | 성공 여부이며 항상 `false`입니다. |
| `error` | object | O | 오류 정보를 포함합니다. |
| `error.code` | string | O | 프론트엔드가 오류를 구분할 때 사용하는 고유 코드입니다. |
| `error.message` | string | O | 사용자에게 표시할 수 있는 안전한 오류 메시지입니다. |

실패 응답에는 `data`를 포함하지 않습니다.

프론트엔드는 `error.message`의 문자열을 비교하지 않고 `error.code`를 기준으로 화면 이동, 재로그인, 알림 표시 등의 동작을 결정합니다.

---

## 5. HTTP 상태 코드

공통 응답의 `success` 값과 별개로 HTTP 상태 코드를 정확하게 사용합니다.

| 상태 코드 | 사용 기준 |
|---|---|
| `200 OK` | 조회 또는 수정 성공 |
| `201 Created` | 새로운 리소스 생성 성공 |
| `204 No Content` | 응답 본문이 없는 성공 |
| `400 Bad Request` | 요청 형식 또는 입력값 오류 |
| `401 Unauthorized` | 인증 정보가 없거나 유효하지 않음 |
| `403 Forbidden` | 인증되었지만 요청을 수행할 권한이 없음 |
| `404 Not Found` | 요청한 리소스가 존재하지 않음 |
| `405 Method Not Allowed` | 지원하지 않는 HTTP Method 요청 |
| `409 Conflict` | 중복 요청 또는 현재 데이터 상태와 충돌 |
| `500 Internal Server Error` | 예상하지 못한 서버 내부 오류 |

오류 응답의 HTTP 상태는 `ErrorCode`에 정의된 `HttpStatus`를 사용합니다.

---

## 6. ErrorCode 구성

모든 공통 오류 코드와 도메인별 오류 코드는 `ErrorCode` 인터페이스를 구현합니다.

```java
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
```

각 항목의 의미는 다음과 같습니다.

| 항목 | 설명 |
|---|---|
| `status` | API 응답에 사용할 HTTP 상태입니다. |
| `code` | 프론트엔드가 오류를 구분하는 고유 코드입니다. |
| `message` | 사용자에게 노출할 수 있는 기본 오류 메시지입니다. |

Java enum 상수 이름은 백엔드 코드에서 의미를 알아보기 쉽게 `UPPER_SNAKE_CASE`로 작성합니다.

```text
MEMBER_NOT_FOUND
DUPLICATE_EMAIL
JOURNEY_ACCESS_DENIED
```

외부 API 응답에 포함되는 실제 오류 코드는 별도의 문자열로 관리합니다.

```text
MEMBER-001
MEMBER-002
JOURNEY-001
```

enum 상수의 이름을 API 오류 코드로 직접 사용하지 않습니다.

---

## 7. 오류 코드 명명 규칙

외부 API에 반환되는 오류 코드는 다음 형식을 사용합니다.

```text
{DOMAIN}-{3자리 번호}
```

도메인별 예시는 다음과 같습니다.

| 도메인 | 코드 예시 |
|---|---|
| 공통 | `COMMON-001` |
| 인증 | `AUTH-001` |
| 회원 | `MEMBER-001` |
| 여행 | `JOURNEY-001` |
| 이벤트 | `EVENT-001` |
| 지도 | `MAP-001` |
| 지갑 | `WALLET-001` |

오류 코드는 다음 규칙을 지킵니다.

- 같은 오류에는 항상 같은 오류 코드를 사용합니다.
- 프론트엔드에서 서로 다르게 처리해야 하는 오류는 코드를 분리합니다.
- HTTP 상태 코드가 같더라도 오류의 원인이나 프론트엔드 처리가 다르면 코드를 분리할 수 있습니다.
- 한 번 외부에 공개한 오류 코드는 다른 의미로 재사용하지 않습니다.
- 배포된 오류 코드의 문자열과 의미를 임의로 변경하지 않습니다.
- 삭제된 오류 코드 번호는 다른 오류에 다시 사용하지 않는 것을 권장합니다.
- Java 예외 클래스 이름을 외부 오류 코드로 사용하지 않습니다.

---

## 8. 공통 오류 코드

특정 도메인에 속하지 않고 모든 API에서 공통으로 발생할 수 있는 오류는 `CommonErrorCode`에 정의합니다.

| enum 상수 | 오류 코드 | HTTP 상태 | 설명 |
|---|---|---:|---|
| `INVALID_INPUT` | `COMMON-001` | 400 | 입력값이 올바르지 않음 |
| `MALFORMED_JSON` | `COMMON-002` | 400 | JSON 문법 오류 또는 요청 본문을 읽을 수 없음 |
| `METHOD_NOT_ALLOWED` | `COMMON-003` | 405 | 지원하지 않는 HTTP Method 요청 |
| `INTERNAL_SERVER_ERROR` | `COMMON-999` | 500 | 예상하지 못한 서버 내부 오류 |

`CommonErrorCode`에는 회원, 여행, 지갑처럼 특정 도메인의 업무 규칙을 추가하지 않습니다.

다음과 같은 오류는 각 도메인의 ErrorCode에 작성합니다.

```text
회원이 존재하지 않음
여행 일정에 접근할 권한이 없음
이미 참가한 이벤트임
지갑 잔액이 부족함
```

---

## 9. 도메인 ErrorCode 작성 위치

도메인별 오류 코드는 각 도메인의 `exception` 패키지에 작성합니다.

```text
me.nawa.auth.exception.AuthErrorCode
me.nawa.member.exception.MemberErrorCode
me.nawa.journey.exception.JourneyErrorCode
me.nawa.event.exception.EventErrorCode
me.nawa.map.exception.MapErrorCode
me.nawa.wallet.exception.WalletErrorCode
```

도메인 담당자는 실제 기능을 구현하면서 필요한 오류 코드만 추가합니다.

사용할 기능이 없는 오류 코드를 미리 모두 작성하지 않습니다.

---

## 10. 도메인 ErrorCode 구현 예시

회원 도메인 오류 코드의 구현 예시는 다음과 같습니다.

```java
package me.nawa.member.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER-001",
            "회원을 찾을 수 없습니다."
    ),

    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "MEMBER-002",
            "이미 사용 중인 이메일입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

도메인 담당자는 공통 `ErrorCode` 인터페이스를 구현하고 `status`, `code`, `message`를 정의합니다.

특별한 추가 데이터나 별도의 처리 방식이 필요하지 않다면 `MemberNotFoundException`과 같은 오류별 예외 클래스를 만들지 않습니다.

---

## 11. BusinessException 사용 규칙

`BusinessException`은 Service에서 발견한 비즈니스 오류를 `GlobalExceptionHandler`까지 전달하는 공통 예외입니다.

도메인 오류 여부는 Service에서 판단합니다.

```java
Member member = memberMapper.findById(memberId);

if (member == null) {
    throw new BusinessException(
            MemberErrorCode.MEMBER_NOT_FOUND
    );
}
```

다음과 같은 비즈니스 규칙 위반은 Service에서 판단합니다.

```text
요청한 리소스가 존재하지 않음
이미 등록된 정보임
현재 상태에서는 요청을 수행할 수 없음
해당 리소스에 접근할 권한이 없음
```

Controller와 Mapper에서는 비즈니스 오류를 직접 결정하지 않습니다.

Mapper가 조회 결과로 null을 반환하면 Service가 결과를 확인하고 적절한 도메인 ErrorCode를 선택해 `BusinessException`을 발생시킵니다.

---

## 12. GlobalExceptionHandler 처리 흐름

Controller가 Service를 호출한 뒤 Service에서 `BusinessException`이 발생하면, 예외는 Controller를 거쳐 Spring MVC까지 전달됩니다.

Spring MVC는 `GlobalExceptionHandler`에서 해당 예외를 처리할 메서드를 찾습니다.

전체 처리 흐름은 다음과 같습니다.

```text
사용자 요청
→ Controller 실행
→ Service 실행
→ BusinessException 발생
→ Controller 밖으로 예외 전달
→ Spring MVC가 GlobalExceptionHandler 호출
→ ErrorCode의 HTTP 상태, 코드, 메시지 확인
→ ApiResponse 실패 JSON 반환
```

Controller는 일반적으로 비즈니스 예외를 직접 `try-catch`하지 않습니다.

`GlobalExceptionHandler`는 다음 예외를 처리합니다.

| 발생한 예외 | 변환할 오류 코드 |
|---|---|
| `BusinessException` | 예외가 가진 도메인 또는 공통 ErrorCode |
| `HttpMessageNotReadableException` | `COMMON-002` |
| `HttpRequestMethodNotSupportedException` | `COMMON-003` |
| 별도로 처리하지 않은 `Exception` | `COMMON-999` |

예상하지 못한 예외는 실제 예외 정보와 stack trace를 서버 로그에 기록합니다.

클라이언트에는 실제 예외 메시지를 반환하지 않고 다음 응답만 반환합니다.

```json
{
  "success": false,
  "error": {
    "code": "COMMON-999",
    "message": "시스템 내부 오류가 발생했습니다."
  }
}
```

---

## 13. Spring Security 예외

Spring Security Filter에서 발생한 인증 및 권한 예외는 Controller가 실행되기 전에 발생할 수 있습니다.

이러한 예외는 `GlobalExceptionHandler`까지 전달되지 않을 수 있으므로 인증 기능을 구현할 때 다음 구성요소에서 별도로 처리합니다.

```text
AuthenticationEntryPoint
→ 인증되지 않은 요청의 401 응답 처리

AccessDeniedHandler
→ 권한이 부족한 요청의 403 응답 처리
```

Security에서 반환하는 오류도 일반 API와 동일한 `ApiResponse` 실패 구조를 사용합니다.

---

## 14. 오류 응답 보안 규칙

API 응답에는 다음 정보를 포함하지 않습니다.

- Java 예외 클래스 이름
- 실제 예외 객체의 `getMessage()` 결과
- stack trace
- SQL 또는 테이블 구조
- 데이터베이스 접속 정보
- JWT와 인증 토큰
- 비밀번호와 인증번호
- 개인정보
- 서버 내부 파일 경로

예상하지 못한 오류의 자세한 원인은 서버 로그를 통해 확인합니다.

로그에도 비밀번호, 토큰, 인증 헤더와 같은 민감정보를 기록하지 않습니다.

---

## 15. 공통 응답을 적용하지 않는 경우

다음 응답에는 공통 JSON 응답 형식을 적용하지 않습니다.

- `204 No Content`
- 파일 및 이미지 다운로드
- 바이너리 응답
- 동영상 또는 파일 스트리밍
- 서버에서 직접 렌더링하는 HTML
- 외부 서비스가 응답 형식을 지정한 Webhook 및 Callback
- Swagger와 정적 리소스

공통 응답을 적용하지 않는 API는 해당 응답 형식을 Swagger에 별도로 명시합니다.

---

## 16. 프론트엔드 처리 규칙

프론트엔드는 다음 기준으로 공통 응답을 처리합니다.

- `success`로 성공과 실패를 구분합니다.
- 성공하면 `data`를 사용합니다.
- 실패하면 `error.code`를 기준으로 오류를 처리합니다.
- `error.message`는 사용자 안내 문구로 사용할 수 있습니다.
- `error.message` 문자열을 비교해 화면 로직을 분기하지 않습니다.
- 알 수 없는 오류 코드를 받으면 공통 오류 메시지나 오류 화면을 표시합니다.
- API 오류 코드가 변경되면 프론트엔드와 백엔드가 함께 변경 내용을 검토합니다.

---

## 17. 공통 응답 예시

### 회원 조회 성공

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "nickname": "여행자"
  }
}
```

### 회원을 찾을 수 없음

```json
{
  "success": false,
  "error": {
    "code": "MEMBER-001",
    "message": "회원을 찾을 수 없습니다."
  }
}
```

### 중복된 이메일

```json
{
  "success": false,
  "error": {
    "code": "MEMBER-002",
    "message": "이미 사용 중인 이메일입니다."
  }
}
```

### 지원하지 않는 HTTP Method

```json
{
  "success": false,
  "error": {
    "code": "COMMON-003",
    "message": "지원하지 않는 HTTP 메서드입니다."
  }
}
```

### 예상하지 못한 서버 오류

```json
{
  "success": false,
  "error": {
    "code": "COMMON-999",
    "message": "시스템 내부 오류가 발생했습니다."
  }
}
```
