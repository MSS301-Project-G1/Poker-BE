package com.msspoker.socialservice.validator;

import com.msspoker.socialservice.exception.SocialApiException;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SocialRequestValidator {
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
