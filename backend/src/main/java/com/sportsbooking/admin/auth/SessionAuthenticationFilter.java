package com.sportsbooking.admin.auth;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE = SessionAuthenticationFilter.class.getName() + ".principal";
    private final SessionRepository sessionRepository;
    private final TokenService tokenService;

    public SessionAuthenticationFilter(SessionRepository sessionRepository, TokenService tokenService) {
        this.sessionRepository = sessionRepository;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String token = readCookie(request, "ADMIN_SESSION");
        if (token != null) {
            SessionPrincipal principal = sessionRepository.findByToken(tokenService.hash(token));
            if (principal != null) {
                request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
