package com.isums.scheduleservice.domains.entities;

import com.isums.common.i18n.TranslationMap;
import com.isums.common.i18n.TranslationMapConverter;
import com.isums.scheduleservice.domains.enums.LeaveRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "leave_requests",
        indexes = {
                @Index(name = "idx_leave_staff", columnList = "staff_id"),
                @Index(name = "idx_leave_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_staff_leave_date",
                        columnNames = {"staff_id", "leave_date"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "note_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap noteTranslations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LeaveRequestStatus status;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "decision_note_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap decisionNoteTranslations;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
