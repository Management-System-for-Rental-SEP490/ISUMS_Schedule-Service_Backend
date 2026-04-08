package com.isums.scheduleservice.infrastructures.grpcs;

import com.isums.maintenanceservice.grpc.GetJobRequest;
import com.isums.maintenanceservice.grpc.GetJobResponse;
import com.isums.maintenanceservice.grpc.MaintenanceServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceClientsGrpc {
    private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub stub;

    public UUID getHouseByJobId(UUID jobId){
        try{
            GetJobRequest req = GetJobRequest.newBuilder().setJobId(jobId.toString()).build();
            GetJobResponse res = stub.getHouseByJobId(req);
            return UUID.fromString(res.getHouseId());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to get houseId for jobId: " + jobId, ex);
        }
    }
}
