package at.hcw.serviceratebackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import at.hcw.serviceratebackend.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Kein Bearer-Token vorhanden -> einfach weiterreichen (Security entscheidet dann, ob der Endpunkt offen ist)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        // Nur wenn das Token gültig ist und noch keine Authentifizierung gesetzt wurde, authentifizieren wir
        if (jwtUtil.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String email       = jwtUtil.extractSubject(token);
            String accountType = jwtUtil.extractAccountType(token);

            boolean active = userRepository.findByEmail(email)
                    .map(user -> "ACTIVE".equals(user.getStatus()))
                    .orElse(false);
            if (!active) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Dein Account wurde deaktiviert. Bitte kontaktiere den Support.\"}");
                return;
            }

            // Die Rolle leiten wir aus dem accountType ab (z.B. ROLE_PROVIDER / ROLE_CUSTOMER)
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + accountType));

            var authToken = UsernamePasswordAuthenticationToken.authenticated(email, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
