package at.hcw.serviceratebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Hasht unsere Passwörter sicher
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Für REST-APIs schalten wir CSRF aus
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("*")); // Erlaubt eurem Frontend den Zugriff (WICHTIG für M3/M4)
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    return config;
                }))
                // Wir nutzen JWTs statt Sessions -> Server bleibt zustandslos
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS-Preflight muss immer durch (sonst scheitert das Frontend)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Login und Registrierung ist für alle offen
                        .requestMatchers("/api/auth/**").permitAll()
                        // Kunden dürfen den Marktplatz ohne Login durchstöbern
                        .requestMatchers(HttpMethod.GET, "/api/services").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/services/*", "/api/providers/**").permitAll()
                        // Bewertungen sind öffentlich lesbar; das Abgeben einer Bewertung erfordert Login
                        .requestMatchers(HttpMethod.GET, "/api/reviews/booking/**", "/api/reviews/service/**").permitAll()
                        // Wetterdaten werden vom Backend als Proxy geliefert, damit API-Keys nicht im Frontend liegen
                        .requestMatchers(HttpMethod.GET, "/api/weather/**").permitAll()
                        // Doku & DB-Konsole
                        .requestMatchers("/h2-console/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/customer/me").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/provider/me").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.PUT, "/api/bookings/*/status").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.POST, "/api/services").hasRole("PROVIDER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Alles andere (Services anlegen/ändern/löschen, Buchungen, ...) braucht ein gültiges Token
                        .anyRequest().authenticated()
                )
                // Unseren JWT-Filter vor den Standard-Login-Filter hängen
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
