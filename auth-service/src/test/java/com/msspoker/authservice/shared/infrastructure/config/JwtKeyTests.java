package com.msspoker.authservice.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyTests {
    @TempDir Path directory;

    @Test
    void signingKeyAndKidRemainStableAcrossConfigurationRestartsAndPublicJwksOmitsPrivateParts() throws Exception {
        Path path = directory.resolve("keys/private.pem");
        JwtConfig config = new JwtConfig();
        var first = config.signingKey(properties(path, true));
        String original = Files.readString(path);
        var restart = config.signingKey(properties(path, false));
        assertThat(restart.getKeyID()).isEqualTo(first.getKeyID());
        assertThat(restart.toRSAPublicKey()).isEqualTo(first.toRSAPublicKey());
        assertThat(Files.readString(path)).isEqualTo(original);
        assertThat(first.toPublicJWK().toJSONObject()).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }

    @Test
    void missingProvisionedKeyFailsWithoutCreatingOrReplacingFiles() {
        Path path = directory.resolve("missing/private.pem");
        assertThatThrownBy(() -> new JwtConfig().signingKey(properties(path, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT signing key is missing");
        assertThat(Files.exists(path)).isFalse();
    }

    private AuthJwtProperties properties(Path path, boolean generate) {
        return new AuthJwtProperties("http://localhost:8090", "poker", Duration.ofMinutes(15), Duration.ofDays(7), path, generate);
    }
}
