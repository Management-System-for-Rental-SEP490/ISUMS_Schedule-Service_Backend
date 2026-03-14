package com.isums.scheduleservice.services;

import com.isums.scheduleservice.domains.dtos.CreateLeaveRequest;
import com.isums.scheduleservice.domains.dtos.LeaveRequestDto;
import com.isums.scheduleservice.domains.dtos.UpdateLeaveStatusRequest;
import com.isums.scheduleservice.domains.entities.LeaveHistory;
import com.isums.scheduleservice.domains.entities.LeaveRequest;
import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.LeaveRequestStatus;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobNeedRescheduleEvent;
import com.isums.scheduleservice.infrastructures.abstracts.LeaveRequestService;
import com.isums.scheduleservice.infrastructures.kafka.JobEventProducer;
import com.isums.scheduleservice.infrastructures.mapper.LeaveMapper;
import com.isums.scheduleservice.infrastructures.repositories.LeaveHistoryRepository;
import com.isums.scheduleservice.infrastructures.repositories.LeaveRequestRepository;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl implements LeaveRequestService {
    private final LeaveHistoryRepository leaveHistoryRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveMapper leaveMapper;
    private final WorkSlotRepository workSlotRepository;
    private final JobEventProducer jobEventProducer;

    @Override
    public LeaveRequestDto createLeaveRequest(CreateLeaveRequest req) {
        try{
            if(leaveRequestRepository.existsByStaffIdAndLeaveDate(req.staffId(),req.leaveDate())){
                throw new RuntimeException("Leave already requested for this date");
            }

            LeaveRequest leave = LeaveRequest.builder()
                    .staffId(req.staffId())
                    .leaveDate(req.leaveDate())
                    .note(req.note())
                    .status(LeaveRequestStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();

            leaveRequestRepository.save(leave);

            return leaveMapper.toDto(leave);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create leave request" + ex.getMessage());
        }
    }

    @Override
    public LeaveRequestDto updateStatus(UUID leaveId, UpdateLeaveStatusRequest request) {
        try{
            LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leaver request not found"));

            leave.setStatus(request.status());
            leave.setManagerId(request.managerId());
            leave.setDecisionNote(request.decisionNote());
            leave.setUpdatedAt(Instant.now());

            if(request.status() == LeaveRequestStatus.APPROVED){
                handleSlotWhenLeaveApproved(leave);
            }

            LeaveRequest save = leaveRequestRepository.save(leave);

            return leaveMapper.toDto(leave);

        } catch (Exception ex) {
            throw new RuntimeException("Can't create leave request" + ex.getMessage());
        }
    }

    private void handleSlotWhenLeaveApproved(LeaveRequest leave){
        List<WorkSlot> slots = workSlotRepository.findSlotsOfStaffInDate(leave.getStaffId(),leave.getLeaveDate());

        for(WorkSlot slot : slots){
            if(slot.getStatus() == SlotStatus.BOOKED){
                slot.setStatus(SlotStatus.NEED_RESCHEDULE);

                JobNeedRescheduleEvent event = new JobNeedRescheduleEvent();
                event.setJobId(slot.getJobId());
                event.setJobType(slot.getJobType().name());
                event.setSlotId(slot.getId());

                jobEventProducer.publishJobNeedReschedule(event);
            }else{
                slot.setStatus(SlotStatus.BLOCKED);
            }
        }

        workSlotRepository.saveAll(slots);

        createLeaveHistory(leave);
    }

    private void createLeaveHistory(LeaveRequest leave){
        LeaveHistory history = LeaveHistory.builder()
                .leaveRequestId(leave.getId())
                .staffId(leave.getStaffId())
                .leaveDate(leave.getLeaveDate())
                .approvedByManagerId(leave.getManagerId())
                .createdAt(Instant.now())
                .build();

        leaveHistoryRepository.save(history);
    }
}
