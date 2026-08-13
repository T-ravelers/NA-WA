package me.nawa.review.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.review.dto.request.MemberReviewCreateRequest;
import me.nawa.review.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {
    @Mock
    private ReviewService reviewService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReviewController(reviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(1L),
                        null,
                        Collections.emptyList()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReview_returns201() throws Exception {
        String request = "{"
                + "\"reviewedAppointmentMemberId\":30,"
                + "\"scores\":{"
                + "\"PUNCTUALITY\":5,"
                + "\"MANNERS\":4,"
                + "\"COMMUNICATION\":5},"
                + "\"keywordCodes\":[\"FRIENDLY\",\"ON_TIME\"]}"
                ;

        String response = mockMvc.perform(
                        post("/api/v1/appointments/10/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(response);
        assertTrue(body.path("success").asBoolean());
        verify(reviewService).createReview(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                any(MemberReviewCreateRequest.class)
        );
    }
}
