package com.xiaowork.autodelivery.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtFilter filter;
    private final ObjectMapper json;
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.requestMatchers("/actuator/health","/error").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/products","/api/v1/products/*").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,ex)->{
                res.setStatus(401);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),Api.Result.error("AUTH_REQUIRED","请先登录或重新登录"));
            }).accessDeniedHandler((req,res,ex)->{
                res.setStatus(403);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),Api.Result.error("FORBIDDEN","无权访问此资源"));
            }))
            .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
    }
}
