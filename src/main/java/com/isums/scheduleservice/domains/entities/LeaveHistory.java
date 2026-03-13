package com.isums.scheduleservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "leave_histories",
        indexes = {
                @Index(name = "idx_leave_history_staff", columnList = "staff_id"),
                @Index(name = "idx_leave_history_date", columnList = "leave_date")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "leave_request_id", nullable = false)
    private UUID leaveRequestId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;


    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(name = "approved_by_manager_id", nullable = false)
    private UUID approvedByManagerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}