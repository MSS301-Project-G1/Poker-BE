package com.msspoker.shopservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
public class ShopServiceApplication {


    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(ShopServiceApplication.class, args);
    }

   
    private static void loadDotEnv() {
        Path[] candidates = {
                Path.of(".env"),
                Path.of("shop-service", ".env")
        };

        for (Path path : candidates) {
            File file = path.toFile();
            if (file.exists() && file.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                            int idx = trimmed.indexOf('=');
                            String key = trimmed.substring(0, idx).trim();
                            String value = trimmed.substring(idx + 1).trim();
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    break;
                } catch (IOException ignored) {
                }
            }
        }
    }
}
