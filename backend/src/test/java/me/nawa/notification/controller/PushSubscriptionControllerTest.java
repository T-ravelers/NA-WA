package me.nawa.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import me.nawa.notification.service.PushProperties;
import me.nawa.notification.service.PushSubscriptionServiceImpl;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 서비스는 실제 구현을 쓰고 Mapper만 흉내 내서, 요청 본문이 비었을 때의 응답까지 이어서 본다. */
@ExtendWith(MockitoExtension.class)
class PushSubscriptionControllerTest {

    private static final String URL = "/api/v1/push-subscriptions";

    @Mock
    private PushSubscriptionMapper mapper;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PushSubscriptionController(
                new PushSubscriptionServiceImpl(mapper, new PushProperties("", "", ""))
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(1L), null, Collections.emptyList()
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 본문 없이 보내면 500이 아니라 400이다. 화면은 항상 본문에 endpoint를 실어 보낸다. */
    @Test
    void unsubscribe_withoutBody_returnsBadRequest() throws Exception {
        assertBadRequest(delete(URL), "COMMON-002");
        assertBadRequest(delete(URL).contentType(MediaType.APPLICATION_JSON), "COMMON-002");

        verifyNoInteractions(mapper);
    }

    @Test
    void unsubscribe_withoutEndpoint_returnsBadRequest() throws Exception {
        for (String body : new String[] {"{}", "{\"endpoint\":null}", "{\"endpoint\":\"  \"}"}) {
            assertBadRequest(delete(URL).contentType(MediaType.APPLICATION_JSON).content(body), "COMMON-001");
        }

        verifyNoInteractions(mapper);
    }

    @Test
    void unsubscribe_withEndpoint_deletesOnlyTheCallersRow() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/abc";

        mockMvc.perform(delete(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + endpoint + "\"}"))
            .andExpect(status().isNoContent());

        verify(mapper).deleteByMemberAndEndpoint(1L, endpoint);
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request, String errorCode) throws Exception {
        String body = mockMvc.perform(request)
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals(errorCode, objectMapper.readTree(body).path("error").path("code").asText());
    }
}
