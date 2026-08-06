package me.nawa.member.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.member.domain.MemberProfile;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.exception.MemberErrorCode;
import me.nawa.member.service.MemberProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private static final Authentication AUTHENTICATION =
            new UsernamePasswordAuthenticationToken(
                    new AuthenticatedMember(1L), null, Collections.emptyList()
            );

    @Mock
    private MemberProfileService memberProfileService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MemberProfileResponse sampleResponse(String language) {
        MemberProfile profile = new MemberProfile();
        profile.setMemberId(1L);
        profile.setDisplayName("여행자");
        profile.setPreferredLanguage(language);
        profile.setPreferredCurrencyCode("JPY");
        profile.setOnboardingCompleted(true);
        return new MemberProfileResponse(profile);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemberController(memberProfileService))
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
    void getMe_returns200WithProfile_whenAuthenticated() throws Exception {
        when(memberProfileService.getProfile(1L)).thenReturn(sampleResponse("en"));

        String body = mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.path("success").asBoolean());
        assertEquals(1, json.path("data").path("memberId").asInt());
        assertEquals("en", json.path("data").path("preferredLanguage").asText());
        assertFalse(json.path("data").path("onboardingRequired").asBoolean());
    }

    @Test
    void patchMe_returns200WithUpdatedProfile() throws Exception {
        when(memberProfileService.updateProfile(eq(1L), any(UpdateMemberProfileRequest.class)))
                .thenReturn(sampleResponse("ja"));

        String body = mockMvc.perform(patch("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"ja\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.path("success").asBoolean());
        assertEquals("ja", json.path("data").path("preferredLanguage").asText());

        ArgumentCaptor<UpdateMemberProfileRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateMemberProfileRequest.class);
        verify(memberProfileService).updateProfile(eq(1L), requestCaptor.capture());
        assertEquals("ja", requestCaptor.getValue().getPreferredLanguage());
        assertNull(requestCaptor.getValue().getPreferredCurrencyCode());
    }

    @Test
    void patchMe_returns400WithErrorBody_whenLanguageUnsupported() throws Exception {
        when(memberProfileService.updateProfile(eq(1L), any(UpdateMemberProfileRequest.class)))
                .thenThrow(new BusinessException(MemberErrorCode.UNSUPPORTED_LANGUAGE));

        String body = mockMvc.perform(patch("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredLanguage\":\"ko\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertFalse(json.path("success").asBoolean());
        assertEquals("MEMBER-002", json.path("error").path("code").asText());
    }
}
