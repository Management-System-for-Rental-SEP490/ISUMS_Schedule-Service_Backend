package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.ConfirmSlotRequest;
import com.isums.scheduleservice.domains.dtos.DaySlotDto;
import com.isums.scheduleservice.domains.dtos.ManualAssignRequest;
import com.isums.scheduleservice.domains.dtos.RescheduleSlotRequest;
import com.isums.scheduleservice.domains.entities.ScheduleTemplate;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.JobType;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.domains.events.JobRescheduledEvent;
import com.isums.scheduleservice.domains.events.JobScheduledEvent;
import com.isums.scheduleservice.exceptions.BadRequestException;
import com.isums.scheduleservice.infrastructures.grpcs.HousesClientsGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.IssueClientGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.MaintenanceClientsGrpc;
import com.isums.scheduleservice.infrastructures.grpcs.UserClientsGrpc;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.mapper.ScheduleMapper;
import com.isums.scheduleservice.infrastructures.repositories.ScheduleTemplateRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkSlotServiceImpl")
class WorkSlotServiceImplTest {

    @Mock private WorkSlotRepository workSlotRepository;
    @Mock private ScheduleTemplateRepository scheduleTemplateRepository;
    @Mock private ScheduleMapper scheduleMapper;
    @Mock private JobEventProducer jobEventProducer;
    @Mock private UserClientsGrpc userClientsGrpc;
    @Mock private HousesClientsGrpc houseClient;
    @Mock private StaffAssignmentService staffAssignmentService;
    @Mock private MaintenanceClientsGrpc maintenanceClient;
    @Mock private IssueClientGrpc issueClient;

    @InjectMocks private WorkSlotServiceImpl service;

    private ScheduleTemplate template;
    private LocalDateTime startTime;

    @BeforeEach
    void setUp() {
        template = ScheduleTemplate.builder()
                .slotMinutes(60)
                .bufferMinutes(0)
                .openTime(LocalTime.of(8, 0))
                .breakStart(LocalTime.of(12, 0))
                .breakEnd(LocalTime.of(13, 0))
                .closeTime(LocalTime.of(17, 0))
                .workingDays("MON,TUE,WED,THU,FRI,SAT,SUN")
                .build();

        startTime = nextWorkingMonday().atTime(9, 0);
    }

    /** Pick a future Monday so validateWorkingHours always passes across days of week. */
    private LocalDate nextWorkingMonday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) d = d.plusDays(1);
        return d;
    }

    @Nested
    @DisplayName("manualAssign")
    class ManualAssign {

        @Test
        @DisplayName("creates a new BOOKED slot when no existing slot and no conflicts")
        void createsNewSlot() {
            UUID jobId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();
            ManualAssignRequest req = new ManualAssignRequest(jobId, staffId, startTime, JobType.ISSUE);

            when(workSlotRepository.existsByJobIdAndStatusIn(jobId, List.of(SlotStatus.BOOKED))).thenReturn(false);
            when(workSlotRepository.findFirstByJobIdAndStatusInOrderByCreatedAtDesc(
                    jobId, List.of(SlotStatus.PENDING, SlotStatus.NEED_RESCHEDULE)))
                    .thenReturn(Optional.empty());
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    startTime.toLocalDate())).thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(staffId, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of());
            when(workSlotRepository.save(any(WorkSlot.class)))
                    .thenAnswer(inv -> { WorkSlot s = inv.getArgument(0); s.setId(UUID.randomUUID()); return s; });

            service.manualAssign(req);

            ArgumentCaptor<WorkSlot> cap = ArgumentCaptor.forClass(WorkSlot.class);
            verify(workSlotRepository).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(SlotStatus.BOOKED);
            assertThat(cap.getValue().getStaffId()).isEqualTo(staffId);
            assertThat(cap.getValue().getEndTime()).isEqualTo(startTime.plusHours(1));

            ArgumentCaptor<JobScheduledEvent> evt = ArgumentCaptor.forClass(JobScheduledEvent.class);
            verify(jobEventProducer).publishJobScheduled(evt.capture());
            assertThat(evt.getValue().getAction()).isEqualTo(JobAction.JOB_SCHEDULED);
        }

        @Test
        @DisplayName("updates pre-existing PENDING slot to BOOKED (reassign)")
        void updatesExistingSlot() {
            UUID jobId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();
            ManualAssignRequest req = new ManualAssignRequest(jobId, staffId, startTime, JobType.ISSUE);

            WorkSlot existing = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .jobType(JobType.ISSUE).status(SlotStatus.PENDING).build();

            when(workSlotRepository.existsByJobIdAndStatusIn(jobId, List.of(SlotStatus.BOOKED))).thenReturn(false);
            when(workSlotRepository.findFirstByJobIdAndStatusInOrderByCreatedAtDesc(
                    jobId, List.of(SlotStatus.PENDING, SlotStatus.NEED_RESCHEDULE)))
                    .thenReturn(Optional.of(existing));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    startTime.toLocalDate())).thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(staffId, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of());
            when(workSlotRepository.save(existing)).thenReturn(existing);

            service.manualAssign(req);

            assertThat(existing.getStatus()).isEqualTo(SlotStatus.BOOKED);
            assertThat(existing.getStaffId()).isEqualTo(staffId);
        }

        @Test
        @DisplayName("throws (wrapped) when job already scheduled")
        void alreadyScheduled() {
            UUID jobId = UUID.randomUUID();
            ManualAssignRequest req = new ManualAssignRequest(jobId, UUID.randomUUID(), startTime, JobType.ISSUE);

            when(workSlotRepository.existsByJobIdAndStatusIn(jobId, List.of(SlotStatus.BOOKED)))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.manualAssign(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Can't create work slot");
            verify(workSlotRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when staff has overlapping slot")
        void conflict() {
            UUID jobId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();
            ManualAssignRequest req = new ManualAssignRequest(jobId, staffId, startTime, JobType.ISSUE);

            when(workSlotRepository.existsByJobIdAndStatusIn(jobId, List.of(SlotStatus.BOOKED))).thenReturn(false);
            when(workSlotRepository.findFirstByJobIdAndStatusInOrderByCreatedAtDesc(any(), anyList()))
                    .thenReturn(Optional.empty());
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(staffId, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of(WorkSlot.builder().id(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> service.manualAssign(req))
                    .isInstanceOf(RuntimeException.class);
            verify(workSlotRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("confirmSlot")
    class ConfirmSlot {

        @Test
        @DisplayName("preserves BadRequestException when slot is already BOOKED (does not wrap to 500)")
        void alreadyConfirmedPropagatesAsBadRequest() {
            UUID jobId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId).status(SlotStatus.BOOKED).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() ->
                    service.confirmSlot(new ConfirmSlotRequest(jobId, startTime)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Slot already confirmed");
        }

        @Test
        @DisplayName("happy path: picks staff, updates slot to BOOKED and publishes JobScheduled")
        void happyPath() {
            UUID jobId = UUID.randomUUID();
            UUID houseId = UUID.randomUUID();
            UUID regionId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();

            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .jobType(JobType.ISSUE).status(SlotStatus.PENDING).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));
            when(maintenanceClient.getHouseByJobId(jobId)).thenReturn(houseId);
            when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
            when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of(staffId));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(template));
            when(staffAssignmentService.pickStaff(any(), any(), any(), any())).thenReturn(staffId);
            when(workSlotRepository.findOverlappingSlots(staffId, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of());
            when(workSlotRepository.save(slot)).thenReturn(slot);

            service.confirmSlot(new ConfirmSlotRequest(jobId, startTime));

            assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
            assertThat(slot.getStaffId()).isEqualTo(staffId);
            verify(jobEventProducer).publishJobScheduled(any(JobScheduledEvent.class));
        }
    }

    @Nested
    @DisplayName("staffConfirmTime")
    class StaffConfirmTime {

        @Test
        @DisplayName("moves PENDING ISSUE slot to WAITING_MANAGER_CONFIRM and publishes event")
        void happyPath() {
            UUID jobId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .staffId(UUID.randomUUID())
                    .jobType(JobType.ISSUE).status(SlotStatus.PENDING).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(slot.getStaffId(), startTime, startTime.plusHours(1)))
                    .thenReturn(List.of());
            when(workSlotRepository.save(slot)).thenReturn(slot);

            service.staffConfirmTime(new ConfirmSlotRequest(jobId, startTime));

            assertThat(slot.getStatus()).isEqualTo(SlotStatus.WAITING_MANAGER_CONFIRM);
            verify(jobEventProducer).publishJobWaitingConfirm(any(JobEvent.class));
        }

        @Test
        @DisplayName("rejects when selected time overlaps another slot of the same staff")
        void rejectsStaffConflict() {
            UUID jobId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .staffId(staffId)
                    .jobType(JobType.ISSUE).status(SlotStatus.PENDING).build();
            WorkSlot existing = WorkSlot.builder()
                    .id(UUID.randomUUID()).staffId(staffId)
                    .status(SlotStatus.DONE)
                    .startTime(startTime).endTime(startTime.plusHours(1)).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(staffId, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of(existing));

            assertThatThrownBy(() -> service.staffConfirmTime(new ConfirmSlotRequest(jobId, startTime)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Staff already has schedule");
            verify(workSlotRepository, never()).save(any());
            verify(jobEventProducer, never()).publishJobWaitingConfirm(any());
        }

        @Test
        @DisplayName("rejects non-ISSUE slots")
        void maintenanceNotAllowed() {
            UUID jobId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .jobType(JobType.MAINTENANCE).status(SlotStatus.PENDING).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() ->
                    service.staffConfirmTime(new ConfirmSlotRequest(jobId, startTime)))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("rejects when slot not in PENDING/WAITING stage")
        void wrongStage() {
            UUID jobId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .jobType(JobType.ISSUE).status(SlotStatus.BOOKED).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() ->
                    service.staffConfirmTime(new ConfirmSlotRequest(jobId, startTime)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("confirmSlotForStaff")
    class ConfirmSlotForStaff {

        @Test
        @DisplayName("promotes WAITING_MANAGER_CONFIRM to BOOKED and publishes Scheduled")
        void happyPath() {
            UUID jobId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId).staffId(UUID.randomUUID())
                    .jobType(JobType.ISSUE).status(SlotStatus.WAITING_MANAGER_CONFIRM)
                    .startTime(startTime).endTime(startTime.plusHours(1)).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));
            when(workSlotRepository.save(slot)).thenReturn(slot);

            service.confirmSlotForStaff(jobId);

            assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
            verify(jobEventProducer).publishJobScheduled(any(JobScheduledEvent.class));
        }

        @Test
        @DisplayName("rejects when slot status is not WAITING_MANAGER_CONFIRM")
        void notWaiting() {
            UUID jobId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .status(SlotStatus.BOOKED).build();

            when(workSlotRepository.findByJobId(jobId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() -> service.confirmSlotForStaff(jobId))
                    .isInstanceOf(RuntimeException.class);
            verify(jobEventProducer, never()).publishJobScheduled(any());
        }
    }

    @Nested
    @DisplayName("cancelSlot")
    class CancelSlot {

        @Test
        @DisplayName("sets status to CANCELLED and returns true")
        void cancels() {
            UUID slotId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder().id(slotId).status(SlotStatus.PENDING).build();
            when(workSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            assertThat(service.cancelSlot(slotId)).isTrue();
            assertThat(slot.getStatus()).isEqualTo(SlotStatus.CANCELLED);
            verify(workSlotRepository).save(slot);
        }

        @Test
        @DisplayName("throws when already CANCELLED")
        void alreadyCancelled() {
            UUID slotId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder().id(slotId).status(SlotStatus.CANCELLED).build();
            when(workSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            assertThatThrownBy(() -> service.cancelSlot(slotId))
                    .isInstanceOf(RuntimeException.class);
            verify(workSlotRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when slot missing")
        void missing() {
            UUID slotId = UUID.randomUUID();
            when(workSlotRepository.findById(slotId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelSlot(slotId))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("rescheduleSlot")
    class Reschedule {

        @Test
        @DisplayName("cancels NEED_RESCHEDULE slot, creates new BOOKED slot and publishes Rescheduled")
        void happyPath() {
            UUID jobId = UUID.randomUUID();
            UUID newStaff = UUID.randomUUID();
            WorkSlot oldSlot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId)
                    .status(SlotStatus.NEED_RESCHEDULE).build();

            when(workSlotRepository.findByJobIdAndStatus(jobId, SlotStatus.NEED_RESCHEDULE))
                    .thenReturn(List.of(oldSlot));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(newStaff, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of());
            when(workSlotRepository.save(any(WorkSlot.class))).thenAnswer(inv -> {
                WorkSlot s = inv.getArgument(0);
                if (s.getId() == null) s.setId(UUID.randomUUID());
                return s;
            });

            service.rescheduleSlot(new RescheduleSlotRequest(
                    jobId, JobType.ISSUE, newStaff, startTime, "reason"));

            assertThat(oldSlot.getStatus()).isEqualTo(SlotStatus.CANCELLED);
            verify(jobEventProducer).publishJobRescheduled(any(JobRescheduledEvent.class));
            verify(workSlotRepository, org.mockito.Mockito.times(2)).save(any(WorkSlot.class));
        }

        @Test
        @DisplayName("throws when no slot needs reschedule")
        void notFound() {
            UUID jobId = UUID.randomUUID();
            when(workSlotRepository.findByJobIdAndStatus(jobId, SlotStatus.NEED_RESCHEDULE))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.rescheduleSlot(new RescheduleSlotRequest(
                    jobId, JobType.ISSUE, UUID.randomUUID(), startTime, "r")))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws when new staff has overlapping slot")
        void conflict() {
            UUID jobId = UUID.randomUUID();
            UUID newStaff = UUID.randomUUID();
            WorkSlot oldSlot = WorkSlot.builder()
                    .id(UUID.randomUUID()).jobId(jobId).status(SlotStatus.NEED_RESCHEDULE).build();

            when(workSlotRepository.findByJobIdAndStatus(jobId, SlotStatus.NEED_RESCHEDULE))
                    .thenReturn(List.of(oldSlot));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlots(newStaff, startTime, startTime.plusHours(1)))
                    .thenReturn(List.of(WorkSlot.builder().id(UUID.randomUUID()).build()));

            assertThatThrownBy(() -> service.rescheduleSlot(new RescheduleSlotRequest(
                    jobId, JobType.ISSUE, newStaff, startTime, "r")))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("markSlotDone")
    class MarkDone {

        @Test
        @DisplayName("flips status to DONE and publishes JOB_COMPLETED for INSPECTION")
        void inspectionDone() {
            UUID slotId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(slotId).jobId(UUID.randomUUID()).staffId(UUID.randomUUID())
                    .jobType(JobType.INSPECTION).status(SlotStatus.BOOKED).build();

            when(workSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            service.markSlotDone(JobEvent.builder().slotId(slotId).build());

            assertThat(slot.getStatus()).isEqualTo(SlotStatus.DONE);
            verify(workSlotRepository).save(slot);

            ArgumentCaptor<JobEvent> cap = ArgumentCaptor.forClass(JobEvent.class);
            verify(jobEventProducer).publishJobCompleted(cap.capture());
            assertThat(cap.getValue().getReferenceType()).isEqualTo("INSPECTION");
            assertThat(cap.getValue().getAction()).isEqualTo(JobAction.JOB_COMPLETED);
        }

        @Test
        @DisplayName("publishes JOB_COMPLETED for ISSUE job types")
        void issueDonePublishesEvent() {
            UUID slotId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(slotId).jobType(JobType.ISSUE).status(SlotStatus.BOOKED).build();

            when(workSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            service.markSlotDone(JobEvent.builder().slotId(slotId).build());

            assertThat(slot.getStatus()).isEqualTo(SlotStatus.DONE);
            verify(jobEventProducer).publishJobCompleted(any(JobEvent.class));
        }

        @Test
        @DisplayName("is idempotent when slot already DONE")
        void alreadyDone() {
            UUID slotId = UUID.randomUUID();
            WorkSlot slot = WorkSlot.builder()
                    .id(slotId).jobType(JobType.INSPECTION).status(SlotStatus.DONE).build();

            when(workSlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

            service.markSlotDone(JobEvent.builder().slotId(slotId).build());

            verify(workSlotRepository, never()).save(any());
            verify(jobEventProducer, never()).publishJobCompleted(any());
        }
    }

    @Nested
    @DisplayName("getSlotsByRange")
    class GetSlotsByRange {

        @Test
        @DisplayName("maps repository result through mapper")
        void happyPath() {
            LocalDate from = LocalDate.of(2026, 4, 1);
            LocalDate to = LocalDate.of(2026, 4, 5);
            List<WorkSlot> slots = List.of(WorkSlot.builder().id(UUID.randomUUID()).build());

            when(workSlotRepository.findByStartTimeBetweenOrderByStartTimeAsc(
                    from.atStartOfDay(), to.plusDays(1).atStartOfDay())).thenReturn(slots);
            when(scheduleMapper.slots(slots)).thenReturn(List.of());

            service.getSlotsByRange(from, to);

            verify(workSlotRepository).findByStartTimeBetweenOrderByStartTimeAsc(
                    from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        }

        @Test
        @DisplayName("throws when start is after end")
        void inverted() {
            LocalDate from = LocalDate.of(2026, 4, 5);
            LocalDate to = LocalDate.of(2026, 4, 1);

            assertThatThrownBy(() -> service.getSlotsByRange(from, to))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getMyAvailableSlotsRange")
    class GetMyAvailable {

        @Test
        @DisplayName("returns a DaySlotDto per date with AVAILABLE/UNAVAILABLE flags")
        void buildsSlots() {
            UUID staffId = UUID.randomUUID();
            LocalDate day = nextWorkingMonday();

            WorkSlot busyAt9 = WorkSlot.builder()
                    .id(UUID.randomUUID()).staffId(staffId)
                    .startTime(day.atTime(9, 0)).endTime(day.atTime(10, 0))
                    .status(SlotStatus.BOOKED).build();

            when(workSlotRepository.findAllInRange(any(UUID.class), any(), any()))
                    .thenReturn(List.of(busyAt9));
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(day))
                    .thenReturn(Optional.of(template));

            List<DaySlotDto> result = service.getMyAvailableSlotsRange(staffId.toString(), day, day);

            assertThat(result).hasSize(1);
            DaySlotDto d = result.getFirst();
            assertThat(d.slots()).anyMatch(s -> s.startTime().equals(LocalTime.of(9, 0))
                    && "UNAVAILABLE".equals(s.status()));
            assertThat(d.slots()).anyMatch(s -> s.startTime().equals(LocalTime.of(8, 0))
                    && "AVAILABLE".equals(s.status()));
        }

        @Test
        @DisplayName("skips slots that overlap the configured break window")
        void skipsBreak() {
            UUID staffId = UUID.randomUUID();
            LocalDate day = nextWorkingMonday();

            when(workSlotRepository.findAllInRange(any(UUID.class), any(), any())).thenReturn(List.of());
            when(scheduleTemplateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(day))
                    .thenReturn(Optional.of(template));

            DaySlotDto d = service.getMyAvailableSlotsRange(staffId.toString(), day, day).getFirst();

            assertThat(d.slots()).noneMatch(s ->
                    !s.startTime().isBefore(LocalTime.of(12, 0))
                            && s.startTime().isBefore(LocalTime.of(13, 0)));
        }
    }

    @Nested
    @DisplayName("getSlotsByDate (Tạo Ca Làm Mới step 2)")
    class GetSlotsByDate {

        private final UUID jobId = UUID.randomUUID();
        private final UUID houseId = UUID.randomUUID();
        private final UUID regionId = UUID.randomUUID();
        private final UUID staffId = UUID.randomUUID();
        private final LocalDate date = nextWorkingMonday();

        @Test
        @DisplayName("REGRESSION: maintenance throws NOT_FOUND — fallback to issue, slots still returned")
        void maintenanceFailsFallbackToIssue() {
            when(maintenanceClient.getHouseByJobId(jobId))
                    .thenThrow(new RuntimeException(
                            "Failed to get houseId for jobId: " + jobId + " [NOT_FOUND: Job not found]"));
            when(issueClient.getHouseByJobId(jobId)).thenReturn(houseId);
            when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
            when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of(staffId));
            when(scheduleTemplateRepository
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlotsForStaffs(any(), any(), any()))
                    .thenReturn(List.of());

            List<DaySlotDto> result = service.getSlotsByDate(jobId, date);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).date()).isEqualTo(date);
            assertThat(result.get(0).slots()).isNotEmpty();
            verify(maintenanceClient).getHouseByJobId(jobId);
            verify(issueClient).getHouseByJobId(jobId);
        }

        @Test
        @DisplayName("REGRESSION: maintenance UNAVAILABLE (Service Connect down) — falls back to issue")
        void maintenanceUnavailableFallsBack() {
            when(maintenanceClient.getHouseByJobId(jobId))
                    .thenThrow(new RuntimeException(
                            "Failed to get houseId for jobId: " + jobId + " [UNAVAILABLE: io exception]"));
            when(issueClient.getHouseByJobId(jobId)).thenReturn(houseId);
            when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
            when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of(staffId));
            when(scheduleTemplateRepository
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlotsForStaffs(any(), any(), any()))
                    .thenReturn(List.of());

            assertThat(service.getSlotsByDate(jobId, date)).hasSize(1);
        }

        @Test
        @DisplayName("REGRESSION: BOTH gRPC fail — error message names both maintenance + issue, NOT just one")
        void bothFailMentionsBothServices() {
            when(maintenanceClient.getHouseByJobId(jobId))
                    .thenThrow(new RuntimeException("Failed to get houseId for jobId: " + jobId + " [NOT_FOUND]"));
            when(issueClient.getHouseByJobId(jobId))
                    .thenThrow(new RuntimeException(
                            "Failed to get houseId for issueId: " + jobId + " [NOT_FOUND]"));

            assertThatThrownBy(() -> service.getSlotsByDate(jobId, date))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Can't get slots by date")
                    .hasMessageContaining("Job not found in both maintenance & issue");

            verify(maintenanceClient).getHouseByJobId(jobId);
            verify(issueClient).getHouseByJobId(jobId);
        }

        @Test
        @DisplayName("houseId resolves but no staff in region — surfaces 'No staff in region' (not gRPC error)")
        void noStaffInRegion() {
            when(maintenanceClient.getHouseByJobId(jobId)).thenReturn(houseId);
            when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
            when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.getSlotsByDate(jobId, date))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Can't get slots by date")
                    .hasMessageContaining("No staff in region");
        }

        @Test
        @DisplayName("error wrapper preserves original cause so logs can show full stack")
        void wrapperPreservesCause() {
            RuntimeException grpcErr = new RuntimeException(
                    "Failed to get houseId for jobId: " + jobId + " [UNAVAILABLE: io exception]");
            when(maintenanceClient.getHouseByJobId(jobId)).thenThrow(grpcErr);
            RuntimeException issueErr = new RuntimeException(
                    "Failed to get houseId for issueId: " + jobId + " [UNAVAILABLE: io exception]");
            when(issueClient.getHouseByJobId(jobId)).thenThrow(issueErr);

            assertThatThrownBy(() -> service.getSlotsByDate(jobId, date))
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("maintenance happy path — does NOT call issue fallback (saves one gRPC roundtrip)")
        void maintenanceHappyDoesNotCallIssue() {
            when(maintenanceClient.getHouseByJobId(jobId)).thenReturn(houseId);
            when(houseClient.getRegionByHouseId(houseId)).thenReturn(regionId);
            when(houseClient.getStaffIdsByRegion(regionId)).thenReturn(List.of(staffId));
            when(scheduleTemplateRepository
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date))
                    .thenReturn(Optional.of(template));
            when(workSlotRepository.findOverlappingSlotsForStaffs(any(), any(), any()))
                    .thenReturn(List.of());

            service.getSlotsByDate(jobId, date);

            verify(maintenanceClient).getHouseByJobId(jobId);
            verify(issueClient, never()).getHouseByJobId(any(UUID.class));
        }
    }
}
