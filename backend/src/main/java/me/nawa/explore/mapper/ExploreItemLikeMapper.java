package me.nawa.explore.mapper;

import java.util.List;
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

    /**
     * 주어진 항목 중 회원이 지금 찜한 항목의 id. 목록의 savedColumn과 같은 조건을 본다.
     *
     * <p>캐시에서 꺼낸 탐색 목록에 회원별 찜 여부를 붙일 때 쓴다(#575). itemIds는 비어 있으면 안 된다.
     */
    List<Long> findSavedItemIds(
        @Param("memberId") long memberId,
        @Param("itemIds") List<Long> itemIds
    );
}
