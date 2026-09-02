package org.example.matcheat.config;

import org.example.matcheat.domain.account.security.AccountSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("accountJwtDecoder") JwtDecoder jwtDecoder,
            @Qualifier("accountJwtAuthenticationConverter")
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            AccountSecurityErrorHandler securityErrorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // 1. CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/signup",
                                "/suspended",
                                "/mypage/**",
                                "/requests/**",
                                "/css/**",
                                "/account/**",
                                "/order-request-test.html",
                                "/ws/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/v1/requests/*/proposals",
                                "/api/v1/quotes/to-buyer"
                        ).hasRole("SELLER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/products/**").hasRole("SELLER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasRole("SELLER")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/mine",
                                "/api/v1/proposals/sent",
                                "/api/v1/proposals/eligibility",
                                "/api/v1/estimates/received",
                                "/api/v1/orders/sales",
                                "/api/v1/requests",
                                "/api/v1/requests/search",
                                "/api/products/*/order-requests/recommendations"
                        ).hasRole("SELLER")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/search",
                                "/api/v1/products/{id}"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // 3. CORS 상세 규칙 설정 Bean 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 프론트엔드 출처(Origin) 지정
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:63342",
                "http://localhost:3000",
                "http://localhost:5173"
        ));

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // 허용할 헤더 (Authorization, Content-Type 등)
        configuration.setAllowedHeaders(List.of("*"));

        // 클라이언트(프론트엔드)에서 응답 헤더를 읽을 수 있도록 허용
        configuration.setExposedHeaders(List.of("Authorization", "Location"));

        // 쿠키/인증 헤더 포함 요청 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
