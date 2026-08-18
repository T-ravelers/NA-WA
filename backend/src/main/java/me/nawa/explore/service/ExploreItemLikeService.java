package me.nawa.explore.service;

import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.explore.dto.response.ExploreItemLikeResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.mapper.ExploreItemLikeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExploreItemLikeService {

    private final ExploreItemLikeMapper likeMapper;

    /**
     * 찜 등록. 이미 찜한 상태면 아무것도 바꾸지 않는다(멱등).
     *
     * 상태가 실제로 바뀔 때만 favorite_count를 올린다 — 되살리기(reviveLike)와
     * 신규 삽입(insertLike)의 영향 행 수가 그 판정이다.
     */
    @Transactional
    public ExploreItemLikeResponse like(long memberId, long itemId) {
        String itemType = likeMapper.findVisibleItemType(itemId);
        if (itemType == null) {
            throw new BusinessException(ExploreErrorCode.ITEM_NOT_FOUND);
        }

        int transitions = likeMapper.reviveLike(itemId, memberId);
        if (transitions == 0) {
            transitions = likeMapper.insertLike(itemId, memberId);
        }
        if (transitions > 0) {
            adjustFavoriteCount(itemType, itemId, 1);
        }

        return new ExploreItemLikeResponse(true);
    }

    /**
     * 찜 취소. 찜한 적이 없거나 이미 취소된 상태면 아무것도 바꾸지 않는다(멱등).
     *
     * 노출이 꺼진(HIDDEN 등) 항목도 취소는 허용한다 — 사용자가 자기 찜 목록을
     * 정리할 수 있어야 하므로 노출 조건 대신 삭제되지 않았는지만 확인한다.
     * 삭제된 항목은 등록과 같이 ITEM_NOT_FOUND다.
     */
    @Transactional
    public ExploreItemLikeResponse unlike(long memberId, long itemId) {
        String itemType = likeMapper.findItemType(itemId);
        if (itemType == null) {
            throw new BusinessException(ExploreErrorCode.ITEM_NOT_FOUND);
        }

        int transitions = likeMapper.softDeleteLike(itemId, memberId);
        if (transitions > 0) {
            adjustFavoriteCount(itemType, itemId, -1);
        }

        return new ExploreItemLikeResponse(false);
    }

    private void adjustFavoriteCount(String itemType, long itemId, int delta) {
        if ("EVENT".equals(itemType)) {
            likeMapper.adjustEventFavoriteCount(itemId, delta);
            return;
        }
        likeMapper.adjustPlaceFavoriteCount(itemId, delta);
    }
}
