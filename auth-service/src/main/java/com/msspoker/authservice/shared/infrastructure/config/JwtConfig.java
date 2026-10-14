package com.msspoker.authservice.shared.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthJwtProperties.class, AuthCookieProperties.class})
public class JwtConfig {
    @Bean
    RSAKey signingKey(AuthJwtProperties properties) throws Exception {
        Path path = properties.privateKeyPath().toAbsolutePath();
        if (!Files.exists(path)) {
            if (!properties.generateKey()) {
                throw new IllegalStateException("JWT signing key is missing; configure AUTH_JWT_PRIVATE_KEY_PATH");
            }
            Files.createDirectories(path.getParent());
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                    + "\n-----END PRIVATE KEY-----\n";
            // CREATE_NEW prevents one instance from silently replacing another instance's key.
            try {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException exception) {
                Files.createFile(path);
            }
            Files.writeString(path, pem, StandardCharsets.US_ASCII, StandardOpenOption.WRITE);
        }
        String pem = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        if (privateKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("JWT RSA key must have at least 2048 bits");
        }
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }

    @Bean
    NimbusJwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    NimbusJwtDecoder jwtDecoder(RSAKey signingKey, AuthJwtProperties properties, Clock clock) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(properties.audience()))));
        return decoder;
    }
}
