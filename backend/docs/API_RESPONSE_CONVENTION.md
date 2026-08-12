# NA-WA API 응답 및 오류 코드 컨벤션

이 문서는 일반 JSON API의 성공·실패 응답, HTTP 상태와 오류 코드 계약을 정의합니다.
백엔드 개발자는 새 API와 도메인 오류를 구현할 때 이 계약을 사용하세요. 프론트엔드
개발자는 `success`와 `error.code`를 기준으로 응답을 처리하세요.

파일 다운로드, 스트리밍처럼 공통 JSON 형식을 사용하지 않는 응답은
[공통 형식을 사용하지 않는 응답](#공통-형식을-사용하지-않는-응답)에서 확인할 수
있습니다.

## 응답 계약

일반 JSON API는 `ApiResponse`를 사용합니다.

- API 응답 필드명은 camelCase를 사용합니다.
- HTTP 상태는 실제 요청 처리 결과에 맞게 반환합니다.
- 성공 응답에는 `data`를 사용합니다.
- 실패 응답에는 `error`를 사용합니다.
- 값이 없는 `data`와 `error`는 JSON에서 제외합니다.
- 프론트엔드는 오류 메시지가 아니라 `error.code`로 오류를 구분합니다.
- `error.message`에는 사용자에게 보여줄 수 있는 안내 문구만 작성합니다.
- 서버 내부 예외 메시지, stack trace, SQL, 개인정보와 인증 정보를 응답에 포함하지
  않습니다.

### 성공 응답

데이터가 있는 성공 응답은 다음 구조를 사용합니다.

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "nickname": "여행자"
  }
}
```

| 필드      | 타입              | 필수 | 설명              |
| --------- | ----------------- | ---: | ----------------- |
| `success` | boolean           |    O | 항상 `true`       |
| `data`    | object 또는 array |    O | API별 응답 데이터 |

Controller에서 `ApiResponse.success(data)`를 반환하세요.

```java
MemberResponse response = memberService.getMember(memberId);

return ApiResponse.success(response);
```

반환 데이터가 없지만 200 응답 본문이 필요하다면 `ApiResponse.success()`를
사용하세요.

```json
{
  "success": true
}
```

```java
return ApiResponse.success();
```

삭제 성공처럼 응답 본문이 필요하지 않다면 `204 No Content`를 사용할 수 있습니다.
204 응답에는 `ApiResponse`와 응답 본문을 사용하지 않습니다.

### 실패 응답

일반 API 실패 응답은 다음 구조를 사용합니다.

```json
{
  "success": false,
  "error": {
    "code": "MEMBER-001",
    "message": "회원을 찾을 수 없습니다."
  }
}
```

| 필드            | 타입    | 필수 | 설명                                       |
| --------------- | ------- | ---: | ------------------------------------------ |
| `success`       | boolean |    O | 항상 `false`                               |
| `error`         | object  |    O | 오류 정보                                  |
| `error.code`    | string  |    O | 프론트엔드가 오류를 구분하는 고유 코드     |
| `error.message` | string  |    O | 사용자에게 표시할 수 있는 안전한 안내 문구 |

실패 응답에는 `data`를 포함하지 않습니다. 프론트엔드는 `error.message` 문자열을
비교하지 않고 `error.code`를 기준으로 화면 이동, 재로그인과 알림 표시를
결정합니다.

## HTTP 상태 선택하기

`success` 값과 별개로 정확한 HTTP 상태를 반환하세요.

| 상태                        | 사용하는 경우                          |
| --------------------------- | -------------------------------------- |
| `200 OK`                    | 조회 또는 수정 성공                    |
| `201 Created`               | 새 리소스 생성 성공                    |
| `204 No Content`            | 응답 본문이 없는 성공                  |
| `400 Bad Request`           | 요청 형식 또는 입력값 오류             |
| `401 Unauthorized`          | 인증 정보가 없거나 유효하지 않음       |
| `403 Forbidden`             | 인증됐지만 요청할 권한이 없음          |
| `404 Not Found`             | 요청한 리소스가 존재하지 않음          |
| `405 Method Not Allowed`    | 지원하지 않는 HTTP Method 요청         |
| `409 Conflict`              | 중복 요청 또는 현재 데이터 상태와 충돌 |
| `500 Internal Server Error` | 예상하지 못한 서버 내부 오류           |

실패 응답의 HTTP 상태는 `ErrorCode`에 정의한 `HttpStatus`에서 가져옵니다.

## ErrorCode 구현하기

공통 오류 코드와 도메인 오류 코드는 `ErrorCode`를 구현합니다.

```java
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
```

| 항목      | 역할                                     |
| --------- | ---------------------------------------- |
| `status`  | API 응답의 HTTP 상태                     |
| `code`    | 프론트엔드가 오류를 구분하는 고유 코드   |
| `message` | 사용자에게 노출할 수 있는 기본 안내 문구 |

Java enum 상수는 `UPPER_SNAKE_CASE`로 작성하세요.

```text
MEMBER_NOT_FOUND
DUPLICATE_EMAIL
JOURNEY_ACCESS_DENIED
```

외부 API에 반환하는 오류 코드는 별도 문자열로 관리합니다.

```text
MEMBER-001
MEMBER-002
JOURNEY-001
```

enum 상수 이름을 외부 오류 코드로 직접 사용하지 마세요.

## 오류 코드 이름 정하기

외부 오류 코드는 `{DOMAIN}-{3자리 번호}` 형식을 사용합니다.

| 도메인 | 코드 예시     |
| ------ | ------------- |
| 공통   | `COMMON-001`  |
| 인증   | `AUTH-001`    |
| 회원   | `MEMBER-001`  |
| 여행   | `JOURNEY-001` |
| 이벤트 | `EVENT-001`   |
| 탐색   | `EXPLORE-001` |
| 지도   | `MAP-001`     |
| 지갑   | `WALLET-001`  |

- 같은 오류에는 항상 같은 코드를 사용합니다.
- 프론트엔드에서 다르게 처리할 오류는 코드를 분리합니다.
- HTTP 상태가 같더라도 원인이나 프론트엔드 처리가 다르면 코드를 분리할 수 있습니다.
- 외부에 공개한 코드의 문자열과 의미를 변경하지 않습니다.
- 삭제한 오류 코드 번호를 다른 오류에 다시 사용하지 않습니다.
- Java 예외 클래스 이름을 외부 오류 코드로 사용하지 않습니다.

## 공통 오류 코드 사용하기

모든 API에서 발생할 수 있고 특정 도메인에 속하지 않는 오류는
`CommonErrorCode`에 정의합니다.

| enum 상수               | 오류 코드    | HTTP 상태 | 의미                                         |
| ----------------------- | ------------ | --------: | -------------------------------------------- |
| `INVALID_INPUT`         | `COMMON-001` |       400 | 입력값이 올바르지 않음                       |
| `MALFORMED_JSON`        | `COMMON-002` |       400 | JSON 문법 오류 또는 요청 본문을 읽을 수 없음 |
| `METHOD_NOT_ALLOWED`    | `COMMON-003` |       405 | 지원하지 않는 HTTP Method 요청               |
| `INTERNAL_SERVER_ERROR` | `COMMON-999` |       500 | 예상하지 못한 서버 내부 오류                 |

회원, 여행과 지갑의 업무 규칙은 `CommonErrorCode`에 추가하지 마세요. 다음 오류는
각 도메인의 ErrorCode에 작성합니다.

```text
회원이 존재하지 않음
여행에 접근할 권한이 없음
이미 참가한 이벤트임
지갑 잔액이 부족함
```

## 도메인 오류 코드 추가하기

도메인 오류 코드는 각 도메인의 `exception` 패키지에 작성합니다.

```text
me.nawa.auth.exception.AuthErrorCode
me.nawa.member.exception.MemberErrorCode
me.nawa.journey.exception.JourneyErrorCode
me.nawa.event.exception.EventErrorCode
me.nawa.map.exception.MapErrorCode
me.nawa.wallet.exception.WalletErrorCode
```

기능을 구현하면서 실제로 필요한 오류 코드만 추가하세요. 사용하지 않는 오류 코드를
미리 만들지 마세요.

회원 도메인 오류 코드는 다음과 같이 구현할 수 있습니다.
(`DUPLICATE_EMAIL`/`MEMBER-005`는 형식을 보여주기 위한 예시일 뿐 실제 구현된 코드가
아닙니다. 실제 코드는 아래 표의 `MEMBER-001`~`MEMBER-004`를 참고하세요.)

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
        "MEMBER-005",
        "이미 사용 중인 이메일입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

특별한 추가 데이터나 별도 처리가 없다면 `MemberNotFoundException`처럼 오류마다 예외
클래스를 만들지 않습니다. 공통 `BusinessException`을 사용하세요.

회원 도메인에는 다음 오류 코드가 실제로 구현돼 있습니다.

| enum 상수             | 오류 코드    | HTTP 상태 | 의미                    |
| ---------------------- | ------------ | --------: | ----------------------- |
| `MEMBER_NOT_FOUND`     | `MEMBER-001` |       404 | 회원 정보를 찾을 수 없음 |
| `UNSUPPORTED_LANGUAGE` | `MEMBER-002` |       400 | 지원하지 않는 언어       |
| `UNSUPPORTED_CURRENCY` | `MEMBER-003` |       400 | 지원하지 않는 통화       |
| `NO_UPDATABLE_FIELD`   | `MEMBER-004` |       400 | 변경할 항목 없음         |

`PATCH /api/v1/members/me`가 `preferredLanguage`·`preferredCurrencyCode`를
부분 수정할 때 사용합니다. 언어 allow-list는 `en`, `ja`, `zh-CN`, `zh-TW`,
`vi`이며 이 백엔드 목록이 정본입니다.

## BusinessException 발생시키기

Service에서 비즈니스 오류를 판단하고 `BusinessException`을 발생시킵니다.

```java
Member member = memberMapper.findById(memberId);

if (member == null) {
    throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
}
```

다음 규칙 위반은 Service에서 판단합니다.

- 요청한 리소스가 존재하지 않음
- 이미 등록된 정보임
- 현재 상태에서는 요청을 수행할 수 없음
- 요청한 리소스에 접근할 권한이 없음

Controller와 Mapper에서 비즈니스 오류를 결정하지 마세요. Mapper가 `null`을
반환하면 Service가 결과를 확인하고 알맞은 ErrorCode를 선택합니다.

## GlobalExceptionHandler 처리 흐름

Service에서 발생한 `BusinessException`은 Controller 밖으로 전달됩니다. Spring MVC는
`GlobalExceptionHandler`에서 처리할 메서드를 찾아 공통 실패 응답으로 변환합니다.

```text
사용자 요청
→ Controller
→ Service
→ BusinessException
→ Spring MVC
→ GlobalExceptionHandler
→ ErrorCode의 HTTP 상태, 코드와 메시지 확인
→ ApiResponse 실패 JSON 반환
```

Controller에서 비즈니스 예외를 반복해서 `try-catch`하지 마세요.

| 발생한 예외                              | 변환할 오류 코드                       |
| ---------------------------------------- | -------------------------------------- |
| `BusinessException`                      | 예외가 가진 도메인 또는 공통 ErrorCode |
| `HttpMessageNotReadableException`        | `COMMON-002`                           |
| `HttpRequestMethodNotSupportedException` | `COMMON-003`                           |
| 별도로 처리하지 않은 `Exception`         | `COMMON-999`                           |

예상하지 못한 예외는 stack trace와 함께 서버 로그에 기록합니다. 클라이언트에는 실제
예외 메시지 대신 다음 응답을 반환합니다.

```json
{
  "success": false,
  "error": {
    "code": "COMMON-999",
    "message": "시스템 내부 오류가 발생했습니다."
  }
}
```

## Spring Security 예외 처리하기

Spring Security Filter의 인증·권한 예외는 Controller가 실행되기 전에 발생할 수
있습니다. 인증 기능을 구현할 때 다음 구성요소에서 공통 실패 응답으로 변환하세요.

| 구성요소                   | 처리할 응답              |
| -------------------------- | ------------------------ |
| `AuthenticationEntryPoint` | 인증되지 않은 요청의 401 |
| `AccessDeniedHandler`      | 권한이 부족한 요청의 403 |

Security에서 반환하는 오류도 `ApiResponse` 실패 구조를 사용합니다.

## 오류 응답에서 보호할 정보

API 응답에 다음 정보를 포함하지 마세요.

- Java 예외 클래스 이름과 실제 `getMessage()` 결과
- stack trace
- SQL, 테이블 구조와 데이터베이스 접속 정보
- JWT와 인증 토큰
- 비밀번호와 인증번호
- 개인정보
- 서버 내부 파일 경로

예상하지 못한 오류의 원인은 서버 로그에서 확인하세요. 로그에도 비밀번호, 토큰과
인증 헤더를 기록하지 않습니다.

## 공통 형식을 사용하지 않는 응답

다음 응답에는 공통 JSON 응답을 적용하지 않습니다.

- `204 No Content`
- 파일과 이미지 다운로드
- 바이너리 응답
- 동영상 또는 파일 스트리밍
- 서버에서 직접 렌더링하는 HTML
- 외부 서비스가 형식을 지정한 Webhook과 Callback
- Swagger와 정적 리소스

공통 형식을 사용하지 않는 API는 실제 응답 형식을 Swagger에 별도로 명시하세요.

## 프론트엔드에서 응답 처리하기

- `success`로 성공과 실패를 구분합니다.
- 성공하면 `data`를 사용합니다.
- 실패하면 `error.code`로 오류를 처리합니다.
- `error.message`는 사용자 안내 문구로 사용할 수 있습니다.
- `error.message` 문자열을 비교해 화면 로직을 분기하지 않습니다.
- 알 수 없는 오류 코드를 받으면 공통 오류 메시지나 오류 화면을 표시합니다.
- 오류 코드를 변경할 때 프론트엔드와 백엔드가 함께 검토합니다.

다음 표는 대표 응답을 빠르게 찾기 위한 참조입니다.

| 상황                      | HTTP 상태 | 응답                         |
| ------------------------- | --------- | ---------------------------- |
| 회원 조회 성공            | 200       | `success: true`, `data` 포함 |
| 회원을 찾을 수 없음       | 404       | `MEMBER-001`                 |
| 지원하지 않는 언어        | 400       | `MEMBER-002`                 |
| Event를 찾을 수 없음      | 404       | `EXPLORE-001`                |
| Place를 찾을 수 없음      | 404       | `EXPLORE-002`                |
| 변경할 항목 없음          | 400       | `MEMBER-004`                 |
| 지원하지 않는 HTTP Method | 405       | `COMMON-003`                 |
| 예상하지 못한 서버 오류   | 500       | `COMMON-999`                 |

정산 API에서 추가로 사용하는 오류 코드는 다음과 같습니다.

| 오류 코드 | HTTP 상태 | 의미 |
| --- | ---: | --- |
| `SETTLEMENT-001` | 404 | 정산 정보를 찾을 수 없음 |
| `SETTLEMENT-002` | 409 | 현재 상태에서 정산 지급을 진행할 수 없음 |
| `SETTLEMENT-003` | 403 | 현재 사용자의 정산 부담금을 찾을 수 없음 |
| `SETTLEMENT-004` | 404 | 정산 가능한 원거래를 찾을 수 없음 |
| `SETTLEMENT-005` | 400 | 정산 생성 정보가 올바르지 않음 |
| `SETTLEMENT-009` | 409 | 같은 멱등성 키의 요청 지문이 기존 요청과 다름 |
| `SETTLEMENT-010` | 409 | 원거래가 이미 다른 정산에 사용됨 |
| `SETTLEMENT-014` | 409 | 정산 지급이 이미 다른 멱등성 키로 처리됨 |
| `SETTLEMENT-015` | 400 | 멱등성 키가 비었거나 길이 제한을 초과함 |
