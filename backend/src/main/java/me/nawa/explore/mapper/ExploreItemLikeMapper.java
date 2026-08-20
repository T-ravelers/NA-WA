package me.nawa.explore.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExploreItemLikeMapper {

    /** 목록·상세와 같은 노출 조건(APPROVED·VISIBLE·미삭제)을 만족하는 항목의 타입. 없으면 null. */
    String findVisibleItemType(@Param("itemId") long itemId);

    /** 노출 여부와 무관하게 존재하는(미삭제) 항목의 타입. 없으면 null. */
    String findItemType(@Param("itemId") long itemId);

    /** soft-delete된 찜을 되살린다. 반환 1이면 상태가 실제로 바뀐 것이다. */
    int reviveLike(@Param("itemId") long itemId, @Param("memberId") long memberId);

    /** 찜 행을 새로 넣는다. 이미 행이 있으면 0을 돌려준다(INSERT IGNORE). */
    int insertLike(@Param("itemId") long itemId, @Param("memberId") long memberId);

    /** 활성 찜을 soft-delete한다. 반환 1이면 상태가 실제로 바뀐 것이다. */
    int softDeleteLike(@Param("itemId") long itemId, @Param("memberId") long memberId);

    int adjustEventFavoriteCount(@Param("itemId") long itemId, @Param("delta") int delta);

    int adjustPlaceFavoriteCount(@Param("itemId") long itemId, @Param("delta") int delta);
}
