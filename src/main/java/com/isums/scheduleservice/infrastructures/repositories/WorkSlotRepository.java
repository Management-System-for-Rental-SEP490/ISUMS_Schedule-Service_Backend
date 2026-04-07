package com.isums.scheduleservice.infrastructures.repositories;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    List<WorkSlot> findByJobIdAndStatus(UUID jobId, SlotStatus status);

    @Query("""
    SELECT w FROM WorkSlot w
    WHERE w.staffId = :staffId
    AND DATE(w.startTime) = :leaveDate
    """)
    List<WorkSlot> findSlotsOfStaffInDate(UUID staffId, LocalDate leaveDate);

    List<WorkSlot> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<WorkSlot> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime startTime, LocalDateTime endTime);
    boolean existsByJobIdAndStatusIn(UUID jobId,List<SlotStatus> statuses);

    @Query("""
    SELECT ws.staffId, COUNT(ws)
    FROM WorkSlot ws
    WHERE ws.staffId IN :staffIds
      AND ws.createdAt >= :startOfMonth
    GROUP BY ws.staffId
    """)
    List<Object[]> countJobsThisMonth(List<UUID> staffIds, Instant startOfMonth);

    @Query("""
    SELECT ws.staffId, COUNT(ws)
    FROM WorkSlot ws
    WHERE ws.staffId IN :staffIds
      AND ws.status IN :statuses
    GROUP BY ws.staffId
    """)
    List<Object[]> countActiveJobs(List<UUID> staffIds, List<SlotStatus> statuses);

    Optional<WorkSlot> findByJobId(UUID jobId);

    Optional<WorkSlot> findFirstByJobIdAndStatusInOrderByCreatedAtDesc(UUID jobId, List<SlotStatus> statuses);

    @Query("""
    SELECT w FROM WorkSlot w
    WHERE w.staffId = :staffId
      AND w.startTime >= :start
      AND w.startTime < :end
""")
    List<WorkSlot> findAllByStaffInRange(UUID staffId, LocalDateTime start, LocalDateTime end);
}
