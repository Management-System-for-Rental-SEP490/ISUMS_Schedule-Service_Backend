package com.isums.scheduleservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "schedule_templates",
        indexes = {
                @Index(name = "idx_template_effective", columnList = "effective_from")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // VD: "MON,TUE,WED,THU,FRI"
    @Column(name = "working_days", nullable = false, length = 64)
    private String workingDays;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}