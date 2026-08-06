# 탐색·이벤트·장소 ERD

탐색 공통 항목과 이벤트·장소 하위 타입, 분류·사용자 반응의 관계를
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

    EVENT ||--o{ EVENT_ACTIVITY : classified_as
    ACTIVITY ||--o{ EVENT_ACTIVITY : classifies

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
        VARCHAR source
        VARCHAR source_item_id
        ENUM event_type
        ENUM event_kind
        JSON operating_hours
        JSON open_days
        BOOLEAN open_weekend
        BOOLEAN opens_late
        JSON image_urls
        JSON links
        VARCHAR reservation_url
        JSON pre_reservation
        VARCHAR contact
        VARCHAR organizer
        DATE start_date
        DATE end_date
        ENUM status
    }

    PLACE {
        BIGINT place_id PK, FK
        VARCHAR source
        VARCHAR source_item_id
        VARCHAR name
        VARCHAR source_url
        JSON image_urls
        VARCHAR address_detail
        TEXT menu_summary
        BOOLEAN is_active
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
- Event·Place의 표시 콘텐츠는 현재 각 기본 테이블에 저장합니다.
- `source`와 `source_item_id` 조합으로 외부 원본 중복을 방지합니다.
- 활동 분류는 이벤트·장소별 연결 테이블로 분리합니다.
- Event 상세 API는 `image_urls`, `operating_hours`, `open_days`를 배열·객체 JSON으로
  반환하며, `links`와 `pre_reservation`도 객체 JSON으로 반환합니다.
- `source`, `source_item_id`, `pipeline_id`와 생성·수정 시각은 운영·동기화용이며
  탐색 화면의 표시 응답에는 포함하지 않습니다.
