package kr.or.kosa.backend.config;

import java.util.List;

import kr.or.kosa.backend.security.jwt.JwtAuthenticationFilter;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/",
                                                                "/auth/github/**",
                                                                "/oauth2/**",
                                                                "/users/register",
                                                                "/users/login",
                                                                "/users/github/link",
                                                                "/email/**",
                                                                "/algo/**",
                                                                "/users/password/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .addFilterBefore(
                                                new JwtAuthenticationFilter(jwtProvider),
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // @Bean
        // public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        //
        // http
        // .cors(Customizer.withDefaults())
        // .csrf(AbstractHttpConfigurer::disable)
        // .sessionManagement(session ->
        // session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        //
        // .authorizeHttpRequests(auth -> auth
        // // .requestMatchers(
        // // "/**"
        // // )
        // // .permitAll()
        // .anyRequest().permitAll());
        //
        // // .addFilterBefore(
        // // new JwtAuthenticationFilter(jwtProvider),
        // // UsernamePasswordAuthenticationFilter.class
        // // );
        //
        // return http.build();
        // }
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
                        throws Exception {
                return config.getAuthenticationManager();
        }
}
