package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WorkSlotRepository extends JpaRepository<WorkSlot, UUID> {
    @Query("""
    SELECT w FROM WorkSlot w
    WHERE w.staffId = :staffId
    AND w.status = 'BOOKED'
    AND w.startTime < :end
    AND w.endTime > :start
    """)
    List<WorkSlot> findOverlappingSlots(UUID staffId, LocalDateTime start, LocalDateTime end);
    List<WorkSlot> findByStaffIdOrderByStartTimeAsc(UUID staffId);
    List<WorkSlot> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime start, LocalDateTime end);
}
