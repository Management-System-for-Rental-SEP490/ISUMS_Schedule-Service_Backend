package com.isums.scheduleservice.controllers;


import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.infrastructures.abstracts.LeaveRequestService;
import com.isums.scheduleservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/leave")
public class LeaveController {
    private final LeaveRequestService leaveRequestService;
    private final UserClientsGrpc userClientsGrpc;


    @PostMapping
    public ApiResponse<LeaveRequestDto> createLeaveRequest(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateLeaveRequest req){
        UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(jwt.getSubject());
        LeaveRequestDto res = leaveRequestService.createLeaveRequest(user.getId(),req);
        return ApiResponses.created(res,"Create leave request successfully");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<LeaveRequestDto> updateStatus(@PathVariable UUID id, @RequestBody UpdateLeaveStatusRequest req){
        LeaveRequestDto res = leaveRequestService.updateStatus(id,req);
        return ApiResponses.ok(res,"update status successfully");
    }
    @GetMapping("/me")
    public ApiResponse<List<LeaveRequestDto>> getLeaveRequestByStaffId(@AuthenticationPrincipal Jwt jwt){
        List<LeaveRequestDto> res = leaveRequestService.getLeaveRequestByStaffId(jwt.getSubject());
        return ApiResponses.ok(res,"update status successfully");
    }
}
