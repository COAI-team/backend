package kr.or.kosa.backend.config;

import java.util.List;
import jakarta.servlet.DispatcherType;
import kr.or.kosa.backend.security.jwt.JwtAuthenticationFilter;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/",
                                "/auth/github/**",
                                "/oauth2/**",
                                "/users/**",
                                "/email/**",
                                "/admin/**",
                                "/codeAnalysis/**",
                                "/api/**",
                                "/ws/**",
                                "/chat/messages"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/freeboard/**",
                                "/codeboard/**",
                                "/comment",
                                "/admin/**",
                                "/comment/**",
                                "/like/*/*/users",
                                "/like/**",
                                "/analysis/**",
                                "/battle/**",
                                "/algo/**"         
                        ).permitAll()
                        .requestMatchers("/battle/**").authenticated()
                        .requestMatchers("/api/mcp/token").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "*",
                "https://www.co-ai.run",
                "https://co-ai.run",
                "https://code-nemsy-frontend.vercel.app"
//                "http://localhost:*",
//                "https://localhost:*",
//                "http://127.0.0.1:*",
//                "https://127.0.0.1:*",
//                "https://*.vercel.app",  // Vercel 프론트엔드 추가
//                "https://code-nemsy-frontend-*.vercel.app"  // 현재 프로젝트
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
