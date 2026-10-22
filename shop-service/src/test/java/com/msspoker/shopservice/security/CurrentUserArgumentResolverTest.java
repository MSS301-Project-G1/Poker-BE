package com.msspoker.shopservice.security;

import com.msspoker.shopservice.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Dummy methods for parameter reflection
    void requiredMethod(@CurrentUser UUID userId) {}
    void optionalMethod(@CurrentUser(required = false) UUID userId) {}

    @Test
    @DisplayName("supportsParameter trả về true khi có @CurrentUser và kiểu UUID")
    void supportsParameter() throws Exception {
        Method method = getClass().getDeclaredMethod("requiredMethod", UUID.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("resolveArgument lấy UUID từ SecurityContextHolder")
    void resolveArgument_fromSecurityContext() throws Exception {
        UUID expectedId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(expectedId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        Method method = getClass().getDeclaredMethod("requiredMethod", UUID.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

        Object result = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("resolveArgument fallback lấy UUID từ header X-User-Id khi SecurityContext trống")
    void resolveArgument_fromHeaderFallback() throws Exception {
        UUID expectedId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", expectedId.toString());

        Method method = getClass().getDeclaredMethod("requiredMethod", UUID.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Object result = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("resolveArgument ném ApiException khi required=true mà không có danh tính")
    void resolveArgument_requiredThrowsWhenUnauthenticated() throws Exception {
        Method method = getClass().getDeclaredMethod("requiredMethod", UUID.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("resolveArgument trả về null khi required=false và không có danh tính")
    void resolveArgument_optionalReturnsNullWhenUnauthenticated() throws Exception {
        Method method = getClass().getDeclaredMethod("optionalMethod", UUID.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

        Object result = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result).isNull();
    }
}
