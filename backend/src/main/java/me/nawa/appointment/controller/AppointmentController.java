package me.nawa.appointment.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentAttendanceRequest;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.appointment.dto.response.AppointmentDetailResponse;
import me.nawa.appointment.dto.response.AppointmentListResponse;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.dto.response.AppointmentParticipationResponse;
import me.nawa.appointment.dto.response.MyOngoingAppointmentResponse;
import me.nawa.appointment.service.AppointmentService;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/{appointmentId}/members")
    @ApiOperation("약속 활성 회원 목록 조회")
    public ApiResponse<List<AppointmentMemberResponse>> getMembers(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId) {
        return ApiResponse.success(
                appointmentService.getAppointmentMembers(
                        member.getMemberId(),
                        appointmentId
                )
        );
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

    @PostMapping("/{appointmentId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("약속 참여 요청")
    public ApiResponse<AppointmentMemberResponse> joinAppointment(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId) {
        return ApiResponse.success(appointmentService.joinAppointment(
                member.getMemberId(),
                appointmentId
        ));
    }

    @DeleteMapping("/{appointmentId}/members/me")
    @ApiOperation("약속 참여 취소")
    public ApiResponse<Void> leaveAppointment(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId) {
        appointmentService.leaveAppointment(
                member.getMemberId(),
                appointmentId
        );
        return ApiResponse.success();
    }

    @GetMapping("/{appointmentId}/members/me")
    @ApiOperation("내 약속 참여 상태 조회")
    public ApiResponse<AppointmentParticipationResponse> getMyParticipation(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId) {
        return ApiResponse.success(
                appointmentService.getMyParticipation(
                        member.getMemberId(),
                        appointmentId
                )
        );
    }

    @PatchMapping("/{appointmentId}/attendance")
    @ApiOperation("방장 출석 확정")
    public ApiResponse<Void> confirmAttendance(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId,
            @RequestBody AppointmentAttendanceRequest request) {
        appointmentService.confirmAttendance(
                member.getMemberId(),
                appointmentId,
                request
        );
        return ApiResponse.success();
    }

    @GetMapping("/me")
    @ApiOperation("내가 참여 중인 진행 중 약속 목록 조회")
    public ApiResponse<List<MyOngoingAppointmentResponse>> getMyOngoingAppointments(
        @AuthenticationPrincipal AuthenticatedMember member
    ){
        return ApiResponse.success(
            appointmentService.getMyOngoingAppointments(member.getMemberId())
        );
    }
}
