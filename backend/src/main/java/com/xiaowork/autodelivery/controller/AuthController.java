package com.xiaowork.autodelivery.controller;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.infra.RedisSupport;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    private final RedisSupport redis;
    @PostMapping("/register") public Api.Result<AuthView> register(@Valid @RequestBody Credentials input,HttpServletRequest req) {redis.rateLimit("register",req.getRemoteAddr(),10);return Api.Result.ok(service.register(input));}
    @PostMapping("/login") public Api.Result<AuthView> login(@Valid @RequestBody Credentials input,HttpServletRequest req) {redis.rateLimit("login",req.getRemoteAddr(),30);return Api.Result.ok(service.login(input));}
    @GetMapping("/me") public Api.Result<UserView> me() {return Api.Result.ok(service.me());}
    @PutMapping("/password") public Api.Result<Void> password(@Valid @RequestBody PasswordChange input) {return Api.Result.ok(service.changePassword(input));}
}
