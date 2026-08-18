package me.nawa.explore.dto.response;

/** 찜 등록·취소 후의 최종 상태. 멱등 재호출도 같은 값을 돌려준다. */
public record ExploreItemLikeResponse(boolean saved) {
}
