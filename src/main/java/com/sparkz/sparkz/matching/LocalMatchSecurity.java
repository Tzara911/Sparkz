package com.sparkz.sparkz.matching;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Profile({"local", "test"})
public class LocalMatchSecurity {
    @Bean
    UserDetailsService demoUsers(@Value("${sparkz.local.password:local-demo}") String password) {
        return new InMemoryUserDetailsManager(
            User.withUsername("00000000-0000-0000-0000-000000000001").password("{noop}" + password).roles("USER").build(),
            User.withUsername("00000000-0000-0000-0000-000000000002").password("{noop}" + password).roles("USER").build());
    }

    @Bean
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers("/matches.html", "/matches.js", "/matches.css", "/sparkz-api.js", "/discovery.html", "/discovery.js", "/discovery.css").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .build();
    }
}
