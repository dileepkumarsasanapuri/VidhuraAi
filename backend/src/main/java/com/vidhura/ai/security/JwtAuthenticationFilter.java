package com.vidhura.ai.security;

import com.vidhura.ai.model.User;
import com.vidhura.ai.repository.InMemoryUserStorage;
import com.vidhura.ai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final InMemoryUserStorage inMemoryUserStorage;

    public static final User GUEST_USER = User.builder()
            .id("default-guest-user")
            .name("Guest User")
            .email("guest@vidhura.ai")
            .settings(User.UserSettings.builder()
                    .profile(User.UserProfile.builder()
                            .name("Guest User")
                            .email("guest@vidhura.ai")
                            .age(35)
                            .occupation("Insurance Professional")
                            .phone("+1 555-0199")
                            .build())
                    .healthProfile(User.HealthProfile.builder()
                            .familyMembers("4")
                            .bloodGroup("O+")
                            .build())
                    .preferences(User.UserPreferences.builder()
                            .language("English")
                            .darkMode(true)
                            .notifications(true)
                            .build())
                    .build())
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        User activeUser = GUEST_USER;

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String userId = tokenProvider.getUserIdFromToken(jwt);

                Optional<User> userOpt = Optional.empty();
                try {
                    userOpt = userRepository.findById(userId);
                } catch (Exception e) {
                    log.warn("MongoDB query failed in JwtFilter, checking in-memory storage: {}", e.getMessage());
                }

                if (userOpt.isEmpty()) {
                    userOpt = inMemoryUserStorage.findById(userId);
                }

                if (userOpt.isPresent()) {
                    activeUser = userOpt.get();
                }
            }
        } catch (Exception ex) {
            log.warn("Using guest user due to auth error: {}", ex.getMessage());
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(activeUser, null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
