package com.isums.scheduleservice.infrastructures.abstracts;

import com.isums.scheduleservice.domains.dtos.CreateLeaveRequest;
import com.isums.scheduleservice.domains.dtos.LeaveRequestDto;
import com.isums.scheduleservice.domains.dtos.UpdateLeaveStatusRequest;

import java.util.List;
import java.util.UUID;

public interface LeaveRequestService {
    LeaveRequestDto createLeaveRequest(CreateLeaveRequest req);
    LeaveRequestDto updateStatus(UUID leaveId, UpdateLeaveStatusRequest request);
    List<LeaveRequestDto> getLeaveRequestByStaffId(UUID staffId);
}
