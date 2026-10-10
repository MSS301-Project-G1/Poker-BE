package com.msspoker.authservice.shared.infrastructure.mail;

import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.AuthMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RegistrationMailSender {
    private final JavaMailSender mailSender;
    private final AuthMailProperties properties;

    public void sendOtp(String email, String code, Duration ttl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject("Poker - Xác thực đăng ký");
        message.setText("Mã OTP đăng ký Poker của bạn: " + code
                + "\nMã có hiệu lực trong " + ttl.toSeconds() + " giây. Không chia sẻ mã này với người khác.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // Never log the message: it contains the raw OTP.
            throw new ApiException(ErrorCode.AUTH_MAIL_UNAVAILABLE);
        }
    }
}
