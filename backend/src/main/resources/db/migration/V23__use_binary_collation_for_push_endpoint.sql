-- push_subscriptions.endpoint를 대소문자를 구분해 비교한다.
--
-- endpoint는 푸시 서비스가 기기마다 정해 준 토큰이 들어 있는 주소이고, 토큰은 대소문자를 구분한다.
-- V22의 기본 collation(utf8mb4_0900_ai_ci)은 대소문자와 악센트를 무시해서, 글자만 같고 대소문자가
-- 다른 두 기기의 endpoint를 같은 값으로 본다. 그러면 UNIQUE에 걸려 한 기기의 등록이 다른 기기의
-- 행을 덮어쓰고(구독의 주인과 키가 바뀐다), 해지도 남의 행을 지울 수 있다.
--
-- 값은 서비스가 URI로 검증한 문자열이라 ASCII 밖의 글자가 들어올 수 있다. ascii 문자셋으로 줄이면
-- 그런 값이 저장 단계에서 500이 되므로 문자셋은 그대로 두고 collation만 바이너리로 바꾼다.
-- ci에서 bin으로 바꾸는 것이라 이미 저장된 행이 UNIQUE를 새로 어기는 경우는 없다.

ALTER TABLE push_subscriptions
    MODIFY endpoint VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
        COMMENT '푸시 서비스가 정해 준 기기별 주소';
