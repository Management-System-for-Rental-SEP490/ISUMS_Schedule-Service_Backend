package com.isums.scheduleservice.infrastructures.mapper;

import com.isums.scheduleservice.domains.dtos.LeaveRequestDto;
import com.isums.scheduleservice.domains.entities.LeaveRequest;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeaveMapper {
    LeaveRequestDto toDto(LeaveRequest entity);

    List<LeaveRequestDto> toDtos(List<LeaveRequest> entities);
}
