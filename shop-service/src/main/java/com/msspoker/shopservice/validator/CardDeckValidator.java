package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.exception.CardSkinExceptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CardDeckValidator {

    public static final String CARD_BACK_CODE = "BACK";
    public static final Set<String> CORE_CARD_CODES;

    private static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    private static final String[] SUITS = {"S", "H", "D", "C"}; // Spades, Hearts, Diamonds, Clubs
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    static {
        Set<String> codes = new LinkedHashSet<>();
        for (String rank : RANKS) {
            for (String suit : SUITS) {
                codes.add(rank + suit);
            }
        }
        codes.add(CARD_BACK_CODE);
        CORE_CARD_CODES = Collections.unmodifiableSet(codes);
    }

    /**
     * Trích xuất mã lá bài chuẩn (viết hoa) từ tên file.
     * Ví dụ: "2h.png" -> "2H", "10s.webp" -> "10S", "card_back.png" -> "BACK"
     */
    public String extractCardCode(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        // Bỏ đường dẫn thư mục nếu có
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

        // Chuẩn hóa tên ví dụ "2_HEARTS" -> "2H", "10_SPADES" -> "10S"
        String normalized = baseName.replace("_", "").replace("-", "");
        if (CORE_CARD_CODES.contains(normalized)) {
            return normalized;
        }

        // Nếu là lá mở rộng (ví dụ JOKER, SPECIAL)
        return baseName;
    }

    /**
     * Kiểm tra tính hợp lệ và đầy đủ của 53 lá bài cốt lõi (52 lá front + 1 lá back).
     */
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

    /**
     * Kiểm tra chống tấn công Zip Slip (chặn Directory Traversal).
     */
    public void validateZipEntry(String entryName) {
        if (entryName == null) {
            throw CardSkinExceptions.zipSlipDetected();
        }
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw CardSkinExceptions.zipSlipDetected();
        }
    }
}
