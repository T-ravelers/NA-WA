# 탐색·이벤트·장소 ERD

탐색 공통 항목과 이벤트·장소 하위 타입, 분류·번역·사용자 반응의 관계를
보여줍니다.

```mermaid
erDiagram
    MEMBERS o|--o{ EXPLORE_ITEMS : creates
    MEMBERS o|--o{ EXPLORE_ITEMS : reviews
    MEMBERS ||--o{ EXPLORE_ITEM_LIKES : likes
    MEMBERS ||--o{ EXPLORE_ITEM_VIEWS : views

    SECTOR ||--o{ ACTIVITY : groups
    EXPLORE_ITEMS ||--o| EVENT : event_subtype
    EXPLORE_ITEMS ||--o| PLACE : place_subtype
    EXPLORE_ITEMS ||--o{ EXPLORE_ITEM_LIKES : receives
    EXPLORE_ITEMS ||--o{ EXPLORE_ITEM_VIEWS : receives

    EVENT ||--o{ EVENT_TRANSLATIONS : translates
    EVENT ||--o{ EVENT_ACTIVITY : classified_as
    ACTIVITY ||--o{ EVENT_ACTIVITY : classifies

    PLACE ||--o{ PLACE_TRANSLATIONS : translates
    PLACE ||--o{ PLACE_ACTIVITY : classified_as
    ACTIVITY ||--o{ PLACE_ACTIVITY : classifies

    MEMBERS {
        BIGINT member_id PK
    }

    SECTOR {
        BIGINT sector_id PK
        VARCHAR sector_code UK
        VARCHAR label_ko
        VARCHAR label_en
    }

    ACTIVITY {
        BIGINT activity_id PK
        BIGINT sector_id FK
        VARCHAR activity_code UK
    }

    EXPLORE_ITEMS {
        BIGINT item_id PK
        BIGINT created_by FK
        BIGINT reviewed_by FK
        ENUM item_type
        ENUM approval_status
        ENUM visibility_status
    }

    EVENT {
        BIGINT event_id PK, FK
        ENUM event_type
        DATE start_date
        DATE end_date
        ENUM status
    }

    PLACE {
        BIGINT place_id PK, FK
        VARCHAR name
        BOOLEAN is_active
    }

    EVENT_TRANSLATIONS {
        BIGINT event_id PK, FK
        VARCHAR language_code PK
        VARCHAR title
    }

    PLACE_TRANSLATIONS {
        BIGINT place_id PK, FK
        VARCHAR language_code PK
        VARCHAR name
    }

    EVENT_ACTIVITY {
        BIGINT event_id PK, FK
        BIGINT activity_id PK, FK
        BOOLEAN is_primary
    }

    PLACE_ACTIVITY {
        BIGINT place_id PK, FK
        BIGINT activity_id PK, FK
        BOOLEAN is_primary
    }

    EXPLORE_ITEM_LIKES {
        BIGINT item_id PK, FK
        BIGINT member_id PK, FK
    }

    EXPLORE_ITEM_VIEWS {
        BIGINT item_id PK, FK
        BIGINT member_id PK, FK
        INT view_count
        DATETIME last_viewed_at
    }
```

- `explore_items`가 검수·공개 상태와 공통 통계를 관리합니다.
- `event`와 `place`는 같은 `item_id`를 PK·FK로 사용하는 하위 타입입니다.
- 활동 분류와 번역은 이벤트·장소별 연결 테이블로 분리합니다.
