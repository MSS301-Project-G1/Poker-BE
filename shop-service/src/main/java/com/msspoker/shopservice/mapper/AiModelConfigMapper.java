package com.msspoker.shopservice.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.dto.request.CreateAiModelConfigRequest;
import com.msspoker.shopservice.dto.response.AiModelConfigResponse;
import com.msspoker.shopservice.entity.AiModelConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AiModelConfigMapper {

    ObjectMapper MAPPER = new ObjectMapper();

    default AiModelConfigResponse toResponse(AiModelConfig entity) {
        if (entity == null) {
            return null;
        }
        List<String> caps = parseCapabilities(entity.getCapabilities());
        return AiModelConfigResponse.builder()
                .id(entity.getId())
                .provider(entity.getProvider())
                .modelName(entity.getModelName())
                .displayName(entity.getDisplayName())
                .capabilities(caps)
                .isActive(entity.isActive())
                .isDefault(entity.isDefault())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    List<AiModelConfigResponse> toResponseList(List<AiModelConfig> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "capabilities", expression = "java(serializeCapabilities(request.capabilities()))")
    AiModelConfig toEntity(CreateAiModelConfigRequest request);

    default List<String> parseCapabilities(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    default String serializeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(capabilities);
        } catch (Exception e) {
            return "[]";
        }
    }
}
