package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.TransactionCounterpartyResponse;
import me.nawa.wallet.dto.response.TransactionDetailResponse;
import me.nawa.wallet.dto.response.TransactionListResponse;
import me.nawa.wallet.service.TransactionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/transactions")
@RequiredArgsConstructor
@Log4j2
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ApiResponse<TransactionListResponse> getTransactions(
        @AuthenticationPrincipal AuthenticatedMember member,
        TransactionSearchCondition condition
        ) {
        return ApiResponse.success(
            transactionService.getTransactions(member.getMemberId(), condition)
        );
    }

    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionDetailResponse> getTransactionDetail(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long transactionId
    ){
        return ApiResponse.success(
            transactionService.getTransactionDetail(member.getMemberId(), transactionId)
        );
    }
}
