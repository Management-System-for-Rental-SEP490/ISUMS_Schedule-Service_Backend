package com.isums.scheduleservice.domains.entities;

import com.isums.scheduleservice.domains.enums.WeeklyScheduleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "weekly_schedules",
        uniqueConstraints = @UniqueConstraint(name = "uk_staff_week", columnNames = {"staff_id", "week_start_date"}),
        indexes = {
                @Index(name = "idx_weekly_region", columnList = "region_id"),
                @Index(name = "idx_weekly_staff", columnList = "staff_id"),
                @Index(name = "idx_weekly_week", columnList = "week_start_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    // Monday của tuần
    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WeeklyScheduleStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}