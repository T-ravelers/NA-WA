# 🤝 T-ravelers Backend 협업 가이드

T-ravelers Backend 개발 시 사용하는 Issue, Branch, Commit, Pull Request 규칙입니다.

## 📌 Issue 규칙

작업을 시작하기 전에 Issue를 생성합니다.

### 제목 형식

```text
[Feat] 한글 설명
[Fix] 한글 설명
[Refactor] 한글 설명
```

### 예시

```text
[Feat] 여행 일정 등록 기능 구현
[Fix] 로그인 실패 시 예외 응답 수정
[Refactor] 회원 서비스 책임 분리
```

## 🌿 Branch 규칙

Branch 이름은 다음 형식을 사용합니다.

```text
<type>/<issue-number>-<short-description>
```

### Type

```text
feat      기능 개발
fix       버그 수정
refactor  리팩터링
chore     설정, 의존성, 기타 작업
test      테스트 추가 또는 수정
```

### 예시

```text
feat/12-create-travel-plan
fix/27-fix-login-error
refactor/35-separate-member-service
chore/41-update-gradle-config
test/46-add-member-service-test
```

## 📝 Commit 규칙

Commit 메시지는 한글로 작성합니다.

```text
<type>: <한글 요약> (#이슈번호)
```

### 예시

```text
feat: 여행 일정 등록 기능 추가 (#12)
fix: 로그인 실패 예외 응답 수정 (#27)
refactor: 회원 조회 책임 분리 (#35)
chore: Gradle 설정 수정 (#41)
test: 회원 서비스 테스트 추가 (#46)
```

## 🚀 Pull Request 규칙

PR 제목은 다음 형식을 사용합니다.

```text
[Type] 한글 설명
```

### 예시

```text
[Feat] 여행 일정 등록 기능 구현
[Fix] 로그인 실패 예외 응답 수정
[Refactor] 회원 서비스 책임 분리
```

PR 본문에는 다음 내용을 작성합니다.

```text
PR 목적
작업 내용
관련 이슈
테스트
체크리스트
리뷰어에게
```

## 🧪 테스트 기준

변경 내용에 맞는 검증 방법을 선택합니다.

```text
Swagger 또는 Postman 수동 확인
단위 테스트
통합 테스트
테스트가 필요하지 않은 변경
```

테스트를 하지 않았다면 이유를 작성합니다.

## ✅ 기본 원칙

- 하나의 Issue, Branch, PR은 하나의 주요 작업만 다룹니다.
- 제목과 Commit 메시지는 한글로 명확하게 작성합니다.
- PR 생성 전 불필요한 로그, 주석, 민감정보를 확인합니다.
- 기존 동작이 바뀌는 경우 테스트 또는 수동 검증 결과를 남깁니다.