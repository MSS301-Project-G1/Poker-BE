package com.msspoker.authservice.mapper;

import com.msspoker.authservice.dto.checkin.response.CheckinResponse;
import com.msspoker.authservice.entity.DailyCheckin;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CheckinMapper {
    @Mapping(target = "checkinId", source = "id")
    @Mapping(target = "makeup", constant = "false")
    CheckinResponse toResponse(DailyCheckin checkin);
}
