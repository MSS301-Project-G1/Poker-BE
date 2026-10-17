package com.msspoker.authservice.shared.infrastructure.storage;

import com.msspoker.authservice.dto.profile.response.AvatarOptionResponse;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.AuthAvatarProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class AvatarStorage {
    private static final List<String> IDS = List.of("spade", "heart", "diamond");
    private static final List<String> NAMES = List.of("Bích", "Cơ", "Rô");
    private final String baseUrl;

    public AvatarStorage(AuthAvatarProperties properties) {
        baseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid public API URL for preset avatars");
        }
    }

    public List<AvatarOptionResponse> presets() {
        return IntStream.range(0, IDS.size())
                .mapToObj(index -> new AvatarOptionResponse(IDS.get(index), NAMES.get(index), presetUrl(IDS.get(index)))).toList();
    }

    public String presetUrl(String id) {
        if (id == null || !IDS.contains(id)) throw new ApiException(ErrorCode.AUTH_AVATAR_INVALID);
        return baseUrl + "/api/profiles/avatars/presets/" + id;
    }

    public Resource presetImage(String id) {
        if (id == null || !IDS.contains(id)) throw new ApiException(ErrorCode.AUTH_NOT_FOUND);
        return new ClassPathResource("avatars/" + id + ".svg");
    }
}
