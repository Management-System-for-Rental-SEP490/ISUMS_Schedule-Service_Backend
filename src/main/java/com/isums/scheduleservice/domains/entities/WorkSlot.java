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
                @Index(name = "idx_staff_date", columnList = "staff_id,start_time"),
                @Index(name = "idx_ticket", columnList = "ticket_id"),
                @Index(name = "idx_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_staff_start_time",
                        columnNames = {"staff_id","start_time"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

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

}