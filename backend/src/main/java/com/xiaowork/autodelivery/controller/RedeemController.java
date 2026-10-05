package com.xiaowork.autodelivery.controller;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.infra.RedisSupport;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.security.Actor;
import com.xiaowork.autodelivery.service.RedeemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/redeem") @RequiredArgsConstructor
public class RedeemController {
    private final RedeemService service;
    private final RedisSupport redis;
    @PostMapping public Api.Result<RedeemView> redeem(@Valid @RequestBody RedeemInput input,HttpServletRequest req) {redis.rateLimit("redeem",Actor.current().id().toString(),20);return Api.Result.ok(service.redeem(input,req.getRemoteAddr()));}
    @GetMapping("/records") public Api.Result<Api.Page<RedeemRecordView>> records(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size) {return Api.Result.ok(service.records(page,size));}
}
