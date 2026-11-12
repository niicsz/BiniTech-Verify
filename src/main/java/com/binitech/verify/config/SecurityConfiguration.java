package com.binitech.verify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain security(HttpSecurity http, @Value("${verify.service-key}") String serviceKey)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/verifications/**", "/api/verifications")
                    .hasRole("SERVICE")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(401))
                    .accessDeniedHandler((request, response, exception) -> response.sendError(401)))
        .addFilterBefore(
            new ServiceKeyFilter(serviceKey), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
