// Sparkz Group
// This file wires up a temporary stand-in login for local development and tests.

package com.sparkz.sparkz.profiles;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@Profile({"local", "test"})
public class LocalLoginConfig {

    public static final String DEMO_USER_1 = "00000000-0000-0000-0000-000000000001";
    public static final String DEMO_USER_2 = "00000000-0000-0000-0000-000000000002";

    @Bean
    UserDetailsService demoUsers(@Value("${sparkz.local.password:local-demo}") String password) {
        return new InMemoryUserDetailsManager(
            User.withUsername(DEMO_USER_1).password("{noop}" + password).roles("USER").build(),
            User.withUsername(DEMO_USER_2).password("{noop}" + password).roles("USER").build());
    }

    @Bean
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/profiles/**", "/api/skills/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable()) // Stateless JSON API, no cookies/forms involved.
            .build();
    }
}
