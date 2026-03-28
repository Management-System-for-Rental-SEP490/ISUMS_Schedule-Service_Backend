package com.isums.scheduleservice.infrastructures.grpcs;

import com.isums.houseservice.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HousesClientsGrpc {
    private final HouseServiceGrpc.HouseServiceBlockingStub stub;

    public UUID getRegionByHouseId(UUID houseId){
        try {
            GetHouseRequest req = GetHouseRequest.newBuilder().setHouseId(houseId.toString()).build();
            GetRegionResponse res = stub.getRegionIdByHouseId(req);
            return UUID.fromString(res.getRegionId());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to get regionId for houseId: " + houseId, ex);
        }
    }

    public List<UUID> getStaffIdsByRegion(UUID regionId){
        try{
            GetRegionRequest req = GetRegionRequest.newBuilder().setRegionId(regionId.toString()).build();
            GetStaffByRegionResponse res = stub.getStaffByRegion(req);
            return res.getStaffIdsList().stream().map(UUID::fromString).toList();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to get staffIds for regionId: " + regionId, ex);
        }
    }
}
