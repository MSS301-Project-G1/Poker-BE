package com.msspoker.authservice.controller;

import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Hidden
@RestController
@RequiredArgsConstructor
public class JwksController {
    private final JwtService jwtService;

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> keys() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jwtService.publicKeys());
    }
}
