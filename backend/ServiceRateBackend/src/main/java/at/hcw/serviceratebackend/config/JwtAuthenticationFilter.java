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
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_ACCOUNT_TYPES = Set.of("CUSTOMER", "PROVIDER", "ADMIN");

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
            String email = jwtUtil.extractSubject(token);
            var user = userRepository.findByEmail(email).orElse(null);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Dein Account wurde deaktiviert. Bitte kontaktiere den Support.\"}");
                return;
            }

            String accountType = user.getAccountType();
            if (!ALLOWED_ACCOUNT_TYPES.contains(accountType)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Account besitzt keine gültige Rolle.\"}");
                return;
            }

            // Die aktuelle Rolle stammt ausschließlich aus der Datenbank, nie aus einem Token-Claim.
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + accountType));

            var authToken = UsernamePasswordAuthenticationToken.authenticated(email, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
