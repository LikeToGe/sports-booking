package com.sportsbooking.admin.mini;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.sportsbooking.admin.auth.TokenService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MiniSessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE = MiniSessionAuthenticationFilter.class.getName() + ".principal";
    private final MiniSessionRepository miniSessionRepository;
    private final TokenService tokenService;

    public MiniSessionAuthenticationFilter(MiniSessionRepository miniSessionRepository, TokenService tokenService) {
        this.miniSessionRepository = miniSessionRepository;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            if (StringUtils.hasText(token)) {
                MiniSessionPrincipal principal = miniSessionRepository.findByAccessTokenHash(tokenService.hash(token));
                if (principal != null) {
                    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
