package at.hcw.serviceratebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
public class SecurityConfig {

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
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    return config;
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Login und Registrierung ist für alle offen
                        .requestMatchers("/h2-console/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll() // Doku & DB-Konsole
                        .anyRequest().permitAll() // VORERST für den Development-Speed lassen wir noch alles offen. Ändern wir auf .authenticated() wenn das Frontend steht!
                );

        return http.build();
    }
}