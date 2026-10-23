package com.msspoker.rankingservice.mapper;

import com.msspoker.rankingservice.dto.response.TutorialResponse;
import com.msspoker.rankingservice.entity.TutorialCompletionEntity;

import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TutorialMapper {
    @Mapping(target = "completed", constant = "true")
    TutorialResponse completed(TutorialCompletionEntity completion);
}
