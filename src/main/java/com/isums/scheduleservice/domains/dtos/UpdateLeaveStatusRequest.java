package com.isums.scheduleservice.domains.dtos;

import com.isums.scheduleservice.domains.enums.LeaveRequestStatus;

import java.util.UUID;

public record UpdateLeaveStatusRequest(
        LeaveRequestStatus status,
        UUID managerId,
        String decisionNote
)  {
}
