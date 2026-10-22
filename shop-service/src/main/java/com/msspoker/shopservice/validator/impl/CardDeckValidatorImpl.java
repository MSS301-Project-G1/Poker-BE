package com.msspoker.shopservice.validator.impl;

import com.msspoker.shopservice.exception.CardSkinExceptions;
import com.msspoker.shopservice.validator.CardDeckValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Implementation cho CardDeckValidator.
 */
@Component
public class CardDeckValidatorImpl implements CardDeckValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    @Override
    public Set<String> getCoreCardCodes() {
        return CORE_CARD_CODES;
    }

    @Override
    public String extractCardCode(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String cleanName = filename.replace("\\", "/");
        if (cleanName.contains("/")) {
            cleanName = cleanName.substring(cleanName.lastIndexOf('/') + 1);
        }

        int dotIdx = cleanName.lastIndexOf('.');
        if (dotIdx <= 0) {
            return null;
        }

        String extension = cleanName.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return null;
        }

        String baseName = cleanName.substring(0, dotIdx).toUpperCase(Locale.ROOT).trim();

        if (baseName.equals("BACK") || baseName.equals("CARD_BACK") || baseName.equals("DECK_BACK")) {
            return CARD_BACK_CODE;
        }
        if (baseName.equals("PREVIEW") || baseName.equals("COVER")) {
            return "PREVIEW";
        }

        String normalized = baseName.replace("_", "").replace("-", "");
        if (CORE_CARD_CODES.contains(normalized)) {
            return normalized;
        }

        return baseName;
    }

    @Override
    public void validateCoreCards(Set<String> presentCodes) {
        if (presentCodes == null || presentCodes.isEmpty()) {
            throw CardSkinExceptions.missingCoreCards(new ArrayList<>(CORE_CARD_CODES));
        }

        List<String> missing = new ArrayList<>();
        for (String coreCode : CORE_CARD_CODES) {
            if (!presentCodes.contains(coreCode)) {
                missing.add(coreCode);
            }
        }

        if (!missing.isEmpty()) {
            throw CardSkinExceptions.missingCoreCards(missing);
        }
    }

    @Override
    public void validateZipEntry(String entryName) {
        if (entryName == null) {
            throw CardSkinExceptions.zipSlipDetected();
        }
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw CardSkinExceptions.zipSlipDetected();
        }
    }
}
