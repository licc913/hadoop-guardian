package com.guardian.hadoop.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(GuardianSecurityProperties.class)
public class SecurityConfig {

    private final GuardianSecurityProperties properties;
    private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;

    public SecurityConfig(GuardianSecurityProperties properties,
                          BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) {
        this.properties = properties;
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!properties.isEnabled()) {
            http
                .cors()
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .anyRequest().permitAll();
            return http.build();
        }

        http
            .cors()
            .and()
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .antMatchers("/api/integrations/**").hasRole("ADMIN")
            .antMatchers(HttpMethod.POST, "/api/knowledge/articles").hasRole("ADMIN")
            .antMatchers(HttpMethod.POST, "/api/incidents/*/diagnosis-tasks").hasAnyRole("ADMIN", "OPERATOR")
            .antMatchers("/", "/index.html", "/assets/**", "/incidents/**", "/settings").permitAll()
            .antMatchers("/actuator/health", "/actuator/info").permitAll()
            .antMatchers("/api/auth/login").permitAll()
            .antMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
            .antMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
            .antMatchers(HttpMethod.GET, "/api/**").authenticated()
            .anyRequest().permitAll();

        http.addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
