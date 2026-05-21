package com.isums.scheduleservice.infrastructures.grpcs;

import com.isums.scheduleservice.domains.entities.WorkSlot;
import com.isums.scheduleservice.domains.enums.JobAction;
import com.isums.scheduleservice.domains.enums.SlotStatus;
import com.isums.scheduleservice.domains.events.JobEvent;
import com.isums.scheduleservice.grpc.AutoAssignRequest;
import com.isums.scheduleservice.grpc.AutoAssignResponse;
import com.isums.scheduleservice.grpc.ScheduleServiceGrpc;
import com.isums.scheduleservice.infrastructures.abstracts.AutoAssignStrategy;
import com.isums.scheduleservice.infrastructures.repositories.WorkSlotRepository;
import com.isums.scheduleservice.services.AutoAssignStrategy.AutoAssignStrategyFactory;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleServiceGrpcImpl extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final AutoAssignStrategyFactory factory;
    private final WorkSlotRepository workSlotRepository;

    @Override
    public void autoAssign(AutoAssignRequest request, StreamObserver<AutoAssignResponse> responseObserver) {
        try {
            UUID referenceId = UUID.fromString(request.getReferenceId());
            UUID houseId = request.getHouseId() != null && !request.getHouseId().isBlank()
                    ? UUID.fromString(request.getHouseId()) : null;
            String referenceType = request.getReferenceType();

            log.info("[ScheduleGrpc] AutoAssign referenceId={} type={} houseId={}",
                    referenceId, referenceType, houseId);

            JobEvent event = JobEvent.builder()
                    .referenceId(referenceId)
                    .houseId(houseId)
                    .referenceType(referenceType)
                    .action(JobAction.JOB_CREATED)
                    .build();

            AutoAssignStrategy strategy = factory.getStrategy(referenceType);
            strategy.handle(event);

            Optional<WorkSlot> slot = workSlotRepository.findFirstByJobIdAndStatusInOrderByCreatedAtDesc(
                    referenceId,
                    List.of(SlotStatus.PENDING, SlotStatus.BOOKED, SlotStatus.NEED_RESCHEDULE));

            AutoAssignResponse.Builder resp = AutoAssignResponse.newBuilder();
            if (slot.isPresent()) {
                WorkSlot s = slot.get();
                resp.setSlotId(s.getId() != null ? s.getId().toString() : "")
                        .setStaffId(s.getStaffId() != null ? s.getStaffId().toString() : "")
                        .setStatus("CREATED");
            } else {
                resp.setStatus("NO_SLOT");
            }

            responseObserver.onNext(resp.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.warn("[ScheduleGrpc] AutoAssign invalid args: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("[ScheduleGrpc] AutoAssign failed: {}", e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("No staff available")) {
                responseObserver.onNext(AutoAssignResponse.newBuilder().setStatus("NO_STAFF").build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.INTERNAL
                        .withDescription(e.getMessage())
                        .asRuntimeException());
            }
        }
    }
}
