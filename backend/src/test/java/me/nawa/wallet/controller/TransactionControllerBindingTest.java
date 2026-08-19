package me.nawa.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.TransactionAppliedFilters;
import me.nawa.wallet.dto.response.TransactionListResponse;
import me.nawa.wallet.service.TransactionService;
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

/**
 * 거래 내역 조회의 쿼리 파라미터 바인딩을 확인한다.
 *
 * from·to는 @DateTimeFormat이 없으면 문자열이 LocalDate로 변환되지 않아 BindException(400)이
 * 된다. GlobalExceptionHandler의 BindException 처리는 로그를 남기지 않아 서버 로그만 봐서는
 * 원인이 드러나지 않는다. 기간 필터를 쓰는 화면이 없던 동안 드러나지 않았다.
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerBindingTest {

    private static final Authentication AUTHENTICATION = new UsernamePasswordAuthenticationToken(
        new AuthenticatedMember(1L), null, Collections.emptyList()
    );

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TransactionController(transactionService))
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
    void getTransactions_bindsDateRange() throws Exception {
        when(transactionService.getTransactions(eq(1L), any(TransactionSearchCondition.class)))
            .thenReturn(TransactionListResponse.of(
                List.of(), null, new TransactionAppliedFilters(null, null, null, null)));

        mockMvc.perform(get("/api/v1/me/transactions")
                .param("type", "QR_PAYMENT")
                .param("status", "COMPLETED")
                .param("from", "2026-08-19")
                .param("to", "2026-08-19")
                .param("size", "50"))
            .andExpect(status().isOk());
    }

    @Test
    void getTransactions_bindsWithoutDateRange() throws Exception {
        when(transactionService.getTransactions(eq(1L), any(TransactionSearchCondition.class)))
            .thenReturn(TransactionListResponse.of(
                List.of(), null, new TransactionAppliedFilters(null, null, null, null)));

        mockMvc.perform(get("/api/v1/me/transactions")
                .param("type", "QR_PAYMENT")
                .param("status", "COMPLETED")
                .param("size", "50"))
            .andExpect(status().isOk());
    }

    @Test
    void getTransactions_rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/v1/me/transactions")
                .param("from", "2026-13-99"))
            .andExpect(status().isBadRequest());
    }
}
