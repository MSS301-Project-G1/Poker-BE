package com.msspoker.gameservice.security;

import com.msspoker.gameservice.exception.GameApiException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameIdentityTest {
    @Test
    void jwtRequiresValidSignatureIssuerAudienceExpiryAndUuidSubject() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        HttpServer jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwks.start();
        try {
            GameIdentity identity = new GameIdentity(new MockEnvironment());
            ReflectionTestUtils.setField(identity, "jwkSetUri", "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks");
            ReflectionTestUtils.setField(identity, "issuer", "poker-identity-test");
            ReflectionTestUtils.setField(identity, "audience", "poker-game-test");
            String subject = UUID.randomUUID().toString();
            assertEquals(subject, identity.authenticate(token(key, subject, "poker-identity-test", "poker-game-test", Instant.now().plusSeconds(60)), null).getName());
            assertThrows(GameApiException.class, () -> identity.authenticate(null, subject));
            assertThrows(GameApiException.class, () -> identity.authenticate(token(key, subject, "wrong-issuer", "poker-game-test", Instant.now().plusSeconds(60)), null));
            assertThrows(GameApiException.class, () -> identity.authenticate(token(key, subject, "poker-identity-test", "wrong-audience", Instant.now().plusSeconds(60)), null));
            assertThrows(GameApiException.class, () -> identity.authenticate(token(key, subject, "poker-identity-test", "poker-game-test", Instant.now().minusSeconds(120)), null));
            assertThrows(GameApiException.class, () -> identity.authenticate(token(key, "not-a-uuid", "poker-identity-test", "poker-game-test", Instant.now().plusSeconds(60)), null));
            RSAKey other = new RSAKeyGenerator(2048).keyID("test-key").generate();
            assertThrows(GameApiException.class, () -> identity.authenticate(token(other, subject, "poker-identity-test", "poker-game-test", Instant.now().plusSeconds(60)), null));
        } finally {
            jwks.stop(0);
        }
    }

    private static String token(RSAKey key, String subject, String issuer, String audience, Instant expiry) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().subject(subject).issuer(issuer).audience(audience).expirationTime(Date.from(expiry)).build());
        jwt.sign(new RSASSASigner(key));
        return "Bearer " + jwt.serialize();
    }
}
