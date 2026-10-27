package com.msspoker.shopservice.ai.provider;

import com.msspoker.shopservice.enums.AiProviderType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AiProviderRegistry {

    private final Map<AiProviderType, AiModelProvider> providerMap;
    private final List<AiModelProvider> providers;

    public AiProviderRegistry(List<AiModelProvider> providers) {
        this.providers = List.copyOf(providers);
        Map<AiProviderType, AiModelProvider> map = new EnumMap<>(AiProviderType.class);
        for (AiModelProvider p : providers) {
            map.put(p.getProviderType(), p);
        }
        this.providerMap = Collections.unmodifiableMap(map);
    }

    public Optional<AiModelProvider> findProvider(AiProviderType type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providerMap.get(type));
    }

    public AiModelProvider getRequiredProvider(AiProviderType type) {
        return findProvider(type)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy AI Provider cho loại: " + type));
    }

    public List<AiModelProvider> getAllProviders() {
        return providers;
    }
}
