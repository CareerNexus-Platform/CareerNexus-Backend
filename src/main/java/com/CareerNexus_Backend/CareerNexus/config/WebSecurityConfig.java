package com.CareerNexus_Backend.CareerNexus.config;

import com.CareerNexus_Backend.CareerNexus.security.JwtFilter;
import io.jsonwebtoken.Jwt;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @org.springframework.beans.factory.annotation.Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("Configuring Security Filter Chain...");



        http
                // 1. Enable CORS for Spring Security and link it to your corsConfigurationSource() bean
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 2. Disable CSRF for stateless APIs (common for REST APIs, but understand implications)
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":401,\"error\":\"Sign in to continue.\"}");
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":403,\"error\":\"You do not have access to this action.\"}");
                }))
                // 3. Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/ws/editor/**").permitAll()
                        .requestMatchers("/api/student/profile", "/api/student/change-password").hasRole("student")
                        .requestMatchers("/api/recruiter/profile").hasRole("recruiter")
                        .requestMatchers("/api/tpo/**").hasRole("tpo")
                        .requestMatchers("/api/admin/**").hasRole("admin")
                        .requestMatchers("/api/jobs/post").hasRole("recruiter")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/jobs/*").hasRole("recruiter")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/jobs/*").hasRole("recruiter")
                        .requestMatchers("/api/applications/apply/**", "/api/applications/my-applications", "/api/student/code/**").hasRole("student")
                        .requestMatchers("/api/applications/counts/**", "/api/applications/job/**", "/api/applications/student/applications/**").hasAnyRole("recruiter", "tpo")
                        .requestMatchers("/api/coding-exam/tpo/create", "/api/coding-exam/all", "/api/coding-exam/*/results", "/api/exam/recruiter/**").hasAnyRole("recruiter", "tpo")
                        .requestMatchers("/api/coding-exam/student/**", "/api/coding-exam/*/start", "/api/coding-exam/*/submit", "/api/exam/*/start", "/api/exam/*/submit").hasRole("student")
                        .anyRequest().authenticated() // All other requests require authentication
                )
                // 4. Configure session management to be stateless (important for JWTs)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        ;

        return http.build();
    }
    // This bean is for your controller to use to kick off the authentication process.
    // Spring Boot automatically configures it if you have a UserDetailsService and PasswordEncoder.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // RE-ADDED: Bean for Password Encoder
    // We use BCrypt for strong password hashing
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Bean for CORS Configuration
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow specified origin for development. In production, specify your frontend's URL(s).
        config.addAllowedOrigin("http://localhost:5173");
        java.net.URI frontend = java.net.URI.create(frontendUrl);
        String configuredOrigin = frontend.getScheme() + "://" + frontend.getAuthority();
        if (!configuredOrigin.equals("http://localhost:5173")) config.addAllowedOrigin(configuredOrigin);
        config.addAllowedOrigin("https://3fw2nnrf-5173.inc1.devtunnels.ms");
        // Your React frontend's development URL

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allowed HTTP methods
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept")); // Allowed headers
        config.setAllowCredentials(true); // Allow sending of cookies/authentication headers
        config.setMaxAge(3600L); // How long the pre-flight request can be cached (in seconds)


        // Apply this CORS configuration to all paths
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
