package com.isums.scheduleservice.controllers;


import com.isums.scheduleservice.domains.dtos.*;
import com.isums.scheduleservice.infrastructures.abstracts.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/leave")
public class LeaveController {
    private final LeaveRequestService leaveRequestService;

    @PostMapping
    public ApiResponse<LeaveRequestDto> createLeaveRequest(@RequestBody CreateLeaveRequest req){
        LeaveRequestDto res = leaveRequestService.createLeaveRequest(req);
        return ApiResponses.created(res,"Create leave request successfully");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<LeaveRequestDto> updateStatus(@PathVariable UUID id, @RequestBody UpdateLeaveStatusRequest req){
        LeaveRequestDto res = leaveRequestService.updateStatus(id,req);
        return ApiResponses.ok(res,"update status successfully");
    }
    @GetMapping("/staff/{staffId}")
    public ApiResponse<List<LeaveRequestDto>> getLeaveRequestByStaffId(@PathVariable UUID staffId){
        List<LeaveRequestDto> res = leaveRequestService.getLeaveRequestByStaffId(staffId);
        return ApiResponses.ok(res,"update status successfully");
    }
}
