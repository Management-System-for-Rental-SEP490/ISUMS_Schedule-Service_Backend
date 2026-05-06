package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.CreateLeaveRequest;
import com.isums.scheduleservice.domains.dtos.UpdateLeaveStatusRequest;
import com.isums.scheduleservice.domains.entities.LeaveRequest;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.LeaveRequestStatus;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobNeedRescheduleEvent;
import com.isums.scheduleservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.kafka.LeaveTranslationRequester;
import com.isums.scheduleservice.infrastructures.mapper.LeaveMapper;
import com.isums.scheduleservice.infrastructures.repositories.LeaveHistoryRepository;
import com.isums.scheduleservice.infrastructures.repositories.LeaveRequestRepository;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveRequestServiceImpl")
class LeaveRequestServiceImplTest {

    @Mock private LeaveHistoryRepository historyRepo;
    @Mock private LeaveRequestRepository leaveRepo;
    @Mock private LeaveMapper mapper;
    @Mock private WorkSlotRepository slotRepo;
    @Mock private JobEventProducer jobProducer;
    @Mock private UserClientsGrpc userGrpc;
    @Mock private LeaveTranslationRequester translationRequester;

    @InjectMocks private LeaveRequestServiceImpl service;

    private UUID staffId;
    private LocalDate leaveDate;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        leaveDate = LocalDate.now().plusDays(3);
    }

    @Nested
    @DisplayName("createLeaveRequest")
    class Create {

        @Test
        @DisplayName("saves PENDING leave when date not yet requested")
        void happy() {
            when(leaveRepo.existsByStaffIdAndLeaveDate(staffId, leaveDate)).thenReturn(false);

            service.createLeaveRequest(staffId.toString(),
                    new CreateLeaveRequest(staffId, leaveDate, "sick"));

            ArgumentCaptor<LeaveRequest> cap = ArgumentCaptor.forClass(LeaveRequest.class);
            verify(leaveRepo).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(cap.getValue().getStaffId()).isEqualTo(staffId);
            assertThat(cap.getValue().getNote()).isEqualTo("sick");
        }

        @Test
        @DisplayName("wraps when duplicate leave request for same date")
        void duplicate() {
            when(leaveRepo.existsByStaffIdAndLeaveDate(staffId, leaveDate)).thenReturn(true);

            assertThatThrownBy(() -> service.createLeaveRequest(staffId.toString(),
                    new CreateLeaveRequest(staffId, leaveDate, "sick")))
                    .isInstanceOf(RuntimeException.class);
            verify(leaveRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateStatus — APPROVED flow")
    class UpdateStatus {

        @Test
        @DisplayName("when APPROVED: BOOKED slots become NEED_RESCHEDULE + publishes JobNeedRescheduleEvent")
        void approvedWithBookedSlot() {
            UUID leaveId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();
            LeaveRequest leave = LeaveRequest.builder()
                    .id(leaveId).staffId(staffId).leaveDate(leaveDate)
                    .status(LeaveRequestStatus.PENDING).build();

            WorkSlot bookedSlot = WorkSlot.builder()
                    .id(UUID.randomUUID()).staffId(staffId).jobId(UUID.randomUUID())
                    .jobType(JobType.ISSUE).status(SlotStatus.BOOKED).build();

            when(leaveRepo.findById(leaveId)).thenReturn(Optional.of(leave));
            when(slotRepo.findSlotsOfStaffInDate(staffId, leaveDate)).thenReturn(List.of(bookedSlot));
            when(leaveRepo.save(leave)).thenReturn(leave);

            service.updateStatus(leaveId, new UpdateLeaveStatusRequest(
                    LeaveRequestStatus.APPROVED, managerId, "ok"));

            assertThat(bookedSlot.getStatus()).isEqualTo(SlotStatus.NEED_RESCHEDULE);
            verify(slotRepo).saveAll(List.of(bookedSlot));
            verify(jobProducer).publishJobNeedReschedule(any(JobNeedRescheduleEvent.class));
            verify(historyRepo).save(any());
        }

        @Test
        @DisplayName("when APPROVED: non-BOOKED slots become BLOCKED without publishing event")
        void approvedWithNonBookedSlot() {
            UUID leaveId = UUID.randomUUID();
            LeaveRequest leave = LeaveRequest.builder()
                    .id(leaveId).staffId(staffId).leaveDate(leaveDate)
                    .status(LeaveRequestStatus.PENDING).build();

            WorkSlot availableSlot = WorkSlot.builder()
                    .id(UUID.randomUUID()).staffId(staffId).jobId(UUID.randomUUID())
                    .jobType(JobType.ISSUE).status(SlotStatus.AVAILABLE).build();

            when(leaveRepo.findById(leaveId)).thenReturn(Optional.of(leave));
            when(slotRepo.findSlotsOfStaffInDate(staffId, leaveDate)).thenReturn(List.of(availableSlot));
            when(leaveRepo.save(leave)).thenReturn(leave);

            service.updateStatus(leaveId, new UpdateLeaveStatusRequest(
                    LeaveRequestStatus.APPROVED, UUID.randomUUID(), "ok"));

            assertThat(availableSlot.getStatus()).isEqualTo(SlotStatus.BLOCKED);
            verify(jobProducer, never()).publishJobNeedReschedule(any());
        }

        @Test
        @DisplayName("when REJECTED: no slot impact, no event")
        void rejected() {
            UUID leaveId = UUID.randomUUID();
            LeaveRequest leave = LeaveRequest.builder()
                    .id(leaveId).staffId(staffId).leaveDate(leaveDate)
                    .status(LeaveRequestStatus.PENDING).build();

            when(leaveRepo.findById(leaveId)).thenReturn(Optional.of(leave));
            when(leaveRepo.save(leave)).thenReturn(leave);

            service.updateStatus(leaveId, new UpdateLeaveStatusRequest(
                    LeaveRequestStatus.REJECTED, UUID.randomUUID(), "not allowed"));

            assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
            verify(slotRepo, never()).saveAll(any());
            verify(jobProducer, never()).publishJobNeedReschedule(any());
        }

        @Test
        @DisplayName("wraps when leave request missing")
        void missing() {
            UUID leaveId = UUID.randomUUID();
            when(leaveRepo.findById(leaveId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateStatus(leaveId,
                    new UpdateLeaveStatusRequest(LeaveRequestStatus.APPROVED, UUID.randomUUID(), null)))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}

