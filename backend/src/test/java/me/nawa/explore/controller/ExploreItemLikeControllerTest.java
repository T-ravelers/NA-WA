package me.nawa.explore.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.explore.dto.response.ExploreItemLikeResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.service.ExploreItemLikeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ExploreItemLikeControllerTest {

    private static final Authentication AUTHENTICATION =
            new UsernamePasswordAuthenticationToken(
                    new AuthenticatedMember(1L), null, Collections.emptyList()
            );

    @Mock
    private ExploreItemLikeService likeService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExploreItemLikeController(likeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(AUTHENTICATION);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void like_returns200WithSavedTrue() throws Exception {
        when(likeService.like(1L, 10L)).thenReturn(new ExploreItemLikeResponse(true));

        String body = mockMvc.perform(post("/api/v1/explore/items/10/like"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.path("success").asBoolean());
        assertTrue(json.path("data").path("saved").asBoolean());
    }

    @Test
    void unlike_returns200WithSavedFalse() throws Exception {
        when(likeService.unlike(1L, 10L)).thenReturn(new ExploreItemLikeResponse(false));

        String body = mockMvc.perform(delete("/api/v1/explore/items/10/like"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.path("success").asBoolean());
        assertFalse(json.path("data").path("saved").asBoolean());
    }

    @Test
    void like_returns404WithErrorBody_whenItemMissing() throws Exception {
        when(likeService.like(1L, 99L))
                .thenThrow(new BusinessException(ExploreErrorCode.ITEM_NOT_FOUND));

        String body = mockMvc.perform(post("/api/v1/explore/items/99/like"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertFalse(json.path("success").asBoolean());
        assertEquals("EXPLORE-003", json.path("error").path("code").asText());
    }
}
