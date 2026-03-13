package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    boolean existsByStaffIdAndLeaveDate(UUID staffId, LocalDate leaveDate);
}
