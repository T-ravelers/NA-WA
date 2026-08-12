package me.nawa.appointment.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.appointment.dto.response.AppointmentDetailResponse;
import me.nawa.appointment.dto.response.AppointmentListResponse;
import me.nawa.appointment.service.AppointmentService;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointmentService;

    @GetMapping
    @ApiOperation("약속 목록 조회")
    public ApiResponse<AppointmentListResponse> searchAppointments(
            @ModelAttribute AppointmentSearchRequest request) {
        return ApiResponse.success(
                appointmentService.searchAppointments(request)
        );
    }

    @GetMapping("/{appointmentId}")
    @ApiOperation("약속 상세 조회")
    public ApiResponse<AppointmentDetailResponse> getAppointment(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId) {
        return ApiResponse.success(appointmentService.getAppointment(
                member.getMemberId(),
                appointmentId
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("약속 생성")
    public ApiResponse<AppointmentDetailResponse> createAppointment(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody AppointmentCreateRequest request) {
        return ApiResponse.success(appointmentService.toCreatedResponse(
                appointmentService.createAppointment(
                        member.getMemberId(),
                        request
                )
        ));
    }
}
