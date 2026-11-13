package com.msspoker.socialservice.validator;

import com.msspoker.socialservice.exception.SocialApiException;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class SocialRequestValidator {
    private static final int MAX_ACCOUNT_IDS = 100;

    public void accountIds(List<UUID> ids) {
        if (ids.isEmpty() || ids.size() > MAX_ACCOUNT_IDS) {
            throw SocialApiException.invalid("Cần từ 1 đến " + MAX_ACCOUNT_IDS + " tài khoản.");
        }
    }

    public void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw SocialApiException.invalid("page>=0, size từ 1 đến 100.");
        }
    }

    public void requestDirection(String direction) {
        if (!Set.of("INCOMING", "OUTGOING").contains(direction)) {
            throw SocialApiException.invalid("direction phải là INCOMING hoặc OUTGOING.");
        }
    }
}
