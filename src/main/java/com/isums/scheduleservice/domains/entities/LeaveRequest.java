package com.isums.scheduleservice.domains.entities;

import com.isums.scheduleservice.domains.enums.LeaveRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "leave_requests",
        indexes = {
                @Index(name = "idx_leave_staff", columnList = "staff_id"),
                @Index(name = "idx_leave_region", columnList = "region_id"),
                @Index(name = "idx_leave_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    // nghỉ theo ngày (dạng MVP)
    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LeaveRequestStatus status;

    // Manager duyệt ai
    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}