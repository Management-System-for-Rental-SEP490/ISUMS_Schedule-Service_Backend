package com.isums.scheduleservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "schedule_templates",
        uniqueConstraints = @UniqueConstraint(name = "uk_template_staff", columnNames = {"staff_id"}),
        indexes = {
                @Index(name = "idx_template_region", columnList = "region_id"),
                @Index(name = "idx_template_staff", columnList = "staff_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    // VD: "MON,TUE,WED,THU,FRI"
    @Column(name = "working_days", nullable = false, length = 64)
    private String workingDays;

    // VD: [{"start":"08:00","end":"12:00"},{"start":"13:00","end":"17:00"}]
    @Column(name = "working_ranges_json", nullable = false, columnDefinition = "text")
    private String workingRangesJson;

    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}