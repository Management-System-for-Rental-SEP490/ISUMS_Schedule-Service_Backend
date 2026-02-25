package com.isums.scheduleservice.domains.entities;

import com.isums.scheduleservice.domains.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "work_slots",
        indexes = {
                @Index(name = "idx_slot_staff_date", columnList = "staff_id,work_date"),
                @Index(name = "idx_slot_region", columnList = "region_id"),
                @Index(name = "idx_slot_ticket", columnList = "ticket_id"),
                @Index(name = "idx_slot_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "weekly_schedule_id", nullable = false)
    private UUID weeklyScheduleId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SlotStatus status;

    // Link issue/maintenance ticket
    @Column(name = "ticket_id")
    private UUID ticketId;

    // ETA tracking
    @Column(name = "last_eta_minutes")
    private Integer lastEtaMinutes;

    @Column(name = "last_eta_updated_at")
    private LocalDateTime lastEtaUpdatedAt;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes;
}