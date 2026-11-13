package com.msspoker.matchmakingservice.config;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.store.PresenceStore;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.util.UUID;

/** Mỗi request của người chơi (FE hỏi trạng thái mỗi giây) đánh dấu tab của họ vẫn đang mở. */
@Component
@RequiredArgsConstructor
public class PresenceInterceptor implements HandlerInterceptor {
    private final PresenceStore presence;
    private final Clock clock;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        String accountId = request.getHeader(MatchmakingConstants.USER_ID_HEADER);
        if (accountId == null) return true;
        try {
            presence.touch(UUID.fromString(accountId), clock.instant());
        } catch (IllegalArgumentException malformed) {
            // Controller tự trả 400 cho header sai định dạng.
        }
        return true;
    }
}
