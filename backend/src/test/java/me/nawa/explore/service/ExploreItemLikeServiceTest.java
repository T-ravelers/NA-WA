package me.nawa.explore.service;

import me.nawa.common.exception.BusinessException;
import me.nawa.explore.dto.response.ExploreItemLikeResponse;
import me.nawa.explore.mapper.ExploreItemLikeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExploreItemLikeServiceTest {

    @Mock
    private ExploreItemLikeMapper likeMapper;

    @InjectMocks
    private ExploreItemLikeService service;

    @Test
    void like_insertsRow_andRaisesEventCount_whenFirstLike() {
        when(likeMapper.findVisibleItemType(10L)).thenReturn("EVENT");
        when(likeMapper.reviveLike(10L, 1L)).thenReturn(0);
        when(likeMapper.insertLike(10L, 1L)).thenReturn(1);

        ExploreItemLikeResponse response = service.like(1L, 10L);

        assertTrue(response.saved());
        verify(likeMapper).adjustEventFavoriteCount(10L, 1);
    }

    @Test
    void like_revivesSoftDeletedRow_andRaisesPlaceCount() {
        when(likeMapper.findVisibleItemType(20L)).thenReturn("PLACE");
        when(likeMapper.reviveLike(20L, 1L)).thenReturn(1);

        ExploreItemLikeResponse response = service.like(1L, 20L);

        assertTrue(response.saved());
        verify(likeMapper, never()).insertLike(anyLong(), anyLong());
        verify(likeMapper).adjustPlaceFavoriteCount(20L, 1);
    }

    @Test
    void like_keepsCount_whenAlreadyLiked() {
        when(likeMapper.findVisibleItemType(10L)).thenReturn("EVENT");
        when(likeMapper.reviveLike(10L, 1L)).thenReturn(0);
        when(likeMapper.insertLike(10L, 1L)).thenReturn(0);

        ExploreItemLikeResponse response = service.like(1L, 10L);

        assertTrue(response.saved());
        verify(likeMapper, never()).adjustEventFavoriteCount(anyLong(), anyInt());
        verify(likeMapper, never()).adjustPlaceFavoriteCount(anyLong(), anyInt());
    }

    @Test
    void like_throwsItemNotFound_whenItemHiddenOrMissing() {
        when(likeMapper.findVisibleItemType(99L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.like(1L, 99L)
        );

        assertEquals("EXPLORE-003", exception.getErrorCode().getCode());
        verify(likeMapper, never()).insertLike(anyLong(), anyLong());
    }

    @Test
    void unlike_softDeletes_andLowersCount_whenLikeWasActive() {
        when(likeMapper.findItemType(10L)).thenReturn("EVENT");
        when(likeMapper.softDeleteLike(10L, 1L)).thenReturn(1);

        ExploreItemLikeResponse response = service.unlike(1L, 10L);

        assertFalse(response.saved());
        verify(likeMapper).adjustEventFavoriteCount(10L, -1);
    }

    @Test
    void unlike_keepsCount_whenNothingToCancel() {
        when(likeMapper.findItemType(10L)).thenReturn("EVENT");
        when(likeMapper.softDeleteLike(10L, 1L)).thenReturn(0);

        ExploreItemLikeResponse response = service.unlike(1L, 10L);

        assertFalse(response.saved());
        verify(likeMapper, never()).adjustEventFavoriteCount(anyLong(), anyInt());
    }

    @Test
    void unlike_allowsHiddenItem_byCheckingBareExistence() {
        // 노출 조건이 아니라 존재 여부만 본다 — findVisibleItemType을 부르지 않는다.
        when(likeMapper.findItemType(30L)).thenReturn("PLACE");
        when(likeMapper.softDeleteLike(30L, 1L)).thenReturn(1);

        service.unlike(1L, 30L);

        verify(likeMapper, never()).findVisibleItemType(anyLong());
        verify(likeMapper).adjustPlaceFavoriteCount(30L, -1);
    }

    @Test
    void unlike_throwsItemNotFound_whenItemMissing() {
        when(likeMapper.findItemType(99L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.unlike(1L, 99L)
        );

        assertEquals("EXPLORE-003", exception.getErrorCode().getCode());
    }
}
