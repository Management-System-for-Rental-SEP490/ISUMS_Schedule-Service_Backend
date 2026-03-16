package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.LeaveHistory;
import com.isums.scheduleservice.domains.entities.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaveHistoryRepository extends JpaRepository<LeaveHistory, UUID> {

    List<LeaveRequest> getLeaveRequestByStaffId(UUID staffId);
}
