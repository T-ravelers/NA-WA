-- 탐색 행사·장소 목록의 정렬 순서대로 읽을 수 있게 인덱스를 붙인다(#572).
--
-- 목록은 20개씩 끊어 보여 주는데, 정렬 열에 인덱스가 없으면 조건에 맞는 행을 전부
-- 읽어 정렬한 뒤 앞의 20개만 쓴다. 인덱스가 있으면 정렬된 순서로 읽다가 20개를 채우는
-- 순간 멈춘다. 행사·장소 2만 건을 넣은 로컬 측정에서 목록 조회가 40~80ms에서 0.5~5ms로
-- 줄었다. 검색어·좁은 필터 조건도 함께 재서 느려지는 경우가 없는 것을 확인했다.
--
-- 마지막 열(event_id·place_id)은 쿼리의 동점 정렬 키다. 빠지면 같은 시각·같은 조회수끼리
-- 다시 정렬해야 해서 인덱스 순서로 읽고도 멈추지 못한다.
--
-- 장소는 목록이 늘 is_active = TRUE로 거르므로 그 열을 앞에 둔다. 행사는 같은 성격의
-- 고정 조건이 없다(종료일 조건은 범위라 앞에 두면 정렬 순서를 잃는다).
--
-- 마감 임박순(end_date)은 붙이지 않는다. 이미 끝난 행사를 건너뛰며 읽어야 해서 측정에서
-- 43ms가 34ms로 줄어드는 데 그쳤다.

-- ORDER BY e.created_at DESC, e.event_id DESC (기본)
CREATE INDEX idx_event_created
    ON event (created_at, event_id);

-- ORDER BY e.view_count DESC, e.event_id DESC (인기순)
CREATE INDEX idx_event_view_count
    ON event (view_count, event_id);

-- WHERE p.is_active = TRUE ORDER BY p.created_at DESC, p.place_id DESC (기본)
CREATE INDEX idx_place_active_created
    ON place (is_active, created_at, place_id);

-- WHERE p.is_active = TRUE ORDER BY p.view_count DESC, p.place_id DESC (인기순)
CREATE INDEX idx_place_active_view_count
    ON place (is_active, view_count, place_id);
