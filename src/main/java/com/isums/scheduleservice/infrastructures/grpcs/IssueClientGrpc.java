package com.isums.scheduleservice.infrastructures.grpcs;

import com.isums.issueservice.grpc.GetIssueRequest;
import com.isums.issueservice.grpc.GetIssueResponse;
import com.isums.issueservice.grpc.IssueServiceGrpc;
import com.isums.maintenanceservice.grpc.GetJobRequest;
import com.isums.maintenanceservice.grpc.GetJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueClientGrpc {
    private final IssueServiceGrpc.IssueServiceBlockingStub stub;

    public UUID getHouseByJobId(UUID jobId){
        try{
            GetIssueRequest req = GetIssueRequest.newBuilder().setId(jobId.toString()).build();
            GetIssueResponse res = stub.getHouseByIssueId(req);
            return UUID.fromString(res.getHouseId());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to get houseId for issueId: " + jobId, ex);
        }
    }
}
