package com.isums.scheduleservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "staff_workload_monthly",
        uniqueConstraints = @UniqueConstraint(name = "uk_staff_month", columnNames = {"staff_id", "month_key"}),
        indexes = {
                @Index(name = "idx_workload_region", columnList = "region_id"),
                @Index(name = "idx_workload_month", columnList = "month_key")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffWorkloadMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    // format: "2026-02"
    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "total_assigned", nullable = false)
    private Integer totalAssigned;

    @Column(name = "active_jobs", nullable = false)
    private Integer activeJobs;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}