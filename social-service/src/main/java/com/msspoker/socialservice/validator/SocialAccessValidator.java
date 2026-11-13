package com.msspoker.socialservice.validator;

import com.msspoker.socialservice.exception.SocialApiException;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SocialAccessValidator {
    public void distinctAccounts(UUID a, UUID b) {
        if (a.equals(b)) throw SocialApiException.invalid("Không thể thao tác với chính mình.");
    }
}
