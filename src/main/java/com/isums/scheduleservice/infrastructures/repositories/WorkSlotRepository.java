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
        AND w.startTime < :endTime
        AND w.endTime > :startTime
    """)
    List<WorkSlot> findOverlappingSlots(
            UUID staffId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

}
