package com.isums.scheduleservice.domains.entities;

import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "work_slots",
        indexes = {
                @Index(name = "idx_staff_time", columnList = "staff_id,start_time,end_time"),
                @Index(name = "idx_job", columnList = "job_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSlot {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}