package me.nawa.report.controller;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.report.dto.request.ReportCreateRequest;
import me.nawa.report.dto.response.ReportDetailResponse;
import me.nawa.report.dto.response.ReportExpenseCandidateResponse;
import me.nawa.report.dto.response.ReportSummaryResponse;
import me.nawa.report.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/journeys/{tripId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("Create a Report snapshot")
    public ApiResponse<ReportDetailResponse> createReport(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long tripId,
        @RequestBody(required = false) ReportCreateRequest request
    ) {
        return ApiResponse.success(
            reportService.createReport(member.getMemberId(), tripId, request)
        );
    }

    @GetMapping("/journeys/{tripId}/report-expense-candidates")
    @ApiOperation("List eligible Wallet expenses for a Report")
    public ApiResponse<List<ReportExpenseCandidateResponse>> getExpenseCandidates(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long tripId
    ) {
        return ApiResponse.success(
            reportService.getExpenseCandidates(member.getMemberId(), tripId)
        );
    }

    @GetMapping("/reports")
    @ApiOperation("List owned Report snapshots")
    public ApiResponse<List<ReportSummaryResponse>> getReports(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            reportService.getReports(member.getMemberId())
        );
    }

    @GetMapping("/reports/{reportId}")
    @ApiOperation("Get a Report snapshot")
    public ApiResponse<ReportDetailResponse> getReport(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long reportId
    ) {
        return ApiResponse.success(
            reportService.getReport(member.getMemberId(), reportId)
        );
    }
}
