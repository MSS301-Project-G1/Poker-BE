package com.msspoker.shopservice.security;

import com.msspoker.shopservice.exception.AppException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Resolver tự động trích xuất UUID người dùng từ SecurityContextHolder cho các tham số @CurrentUser.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UUID.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUser currentUserAnn = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = (currentUserAnn == null) || currentUserAnn.required();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID accountId) {
            return accountId;
        }

        // Hỗ trợ fallback kiểm tra header trực tiếp nếu SecurityContext chưa nạp (ví dụ trong mock test)
        String userIdHeader = webRequest.getHeader(GatewayAuthenticationFilter.HEADER_USER_ID);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (required) {
            throw AppException.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Yêu cầu đăng nhập để thực hiện hành động này");
        }

        return null;
    }
}
