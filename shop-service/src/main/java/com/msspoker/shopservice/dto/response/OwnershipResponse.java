package com.msspoker.shopservice.dto.response;

import lombok.Builder;

@Builder
public record OwnershipResponse(
        boolean owned
) {}
