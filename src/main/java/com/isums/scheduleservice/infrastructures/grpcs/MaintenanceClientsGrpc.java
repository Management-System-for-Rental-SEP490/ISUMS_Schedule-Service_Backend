package com.isums.scheduleservice.infrastructures.grpcs;

import com.isums.maintenanceservice.grpc.GetJobRequest;
import com.isums.maintenanceservice.grpc.GetJobResponse;
import com.isums.maintenanceservice.grpc.MaintenanceServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceClientsGrpc {
    private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub stub;

    public UUID getHouseByJobId(UUID jobId){
        try {
            GetJobRequest req = GetJobRequest.newBuilder().setJobId(jobId.toString()).build();
            GetJobResponse res = stub.getHouseByJobId(req);
            String houseIdRaw = res.getHouseId();
            if (houseIdRaw == null || houseIdRaw.isBlank()) {
                log.warn("[gRPC] maintenance.getHouseByJobId returned empty houseId jobId={}", jobId);
                throw new RuntimeException("Maintenance returned empty houseId for jobId: " + jobId);
            }
            return UUID.fromString(houseIdRaw);
        } catch (StatusRuntimeException ex) {
            log.warn("[gRPC] maintenance.getHouseByJobId jobId={} status={} desc={}",
                    jobId, ex.getStatus().getCode(), ex.getStatus().getDescription());
            throw new RuntimeException(
                    "Failed to get houseId for jobId: " + jobId
                            + " [" + ex.getStatus().getCode() + ": " + ex.getStatus().getDescription() + "]",
                    ex);
        } catch (RuntimeException ex) {
            log.warn("[gRPC] maintenance.getHouseByJobId jobId={} runtime err: {}", jobId, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("[gRPC] maintenance.getHouseByJobId jobId={} unexpected err", jobId, ex);
            throw new RuntimeException("Failed to get houseId for jobId: " + jobId + " (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")", ex);
        }
    }
}
