package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkSlotRepository extends JpaRepository<WorkSlot, UUID> {
    List<WorkSlot> findAllByOrderByStartTimeAsc();
    List<WorkSlot> findByStaffIdOrderByStartTimeAsc(UUID staffId);

}
