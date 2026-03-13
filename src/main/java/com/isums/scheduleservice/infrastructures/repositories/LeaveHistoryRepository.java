package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.LeaveHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeaveHistoryRepository extends JpaRepository<LeaveHistory, UUID> {
}
