package com.xiaowork.autodelivery.controller;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.common.States.OrderStatus;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/orders") @RequiredArgsConstructor
public class OrderController {
    private final OrderService service;
    @PostMapping public Api.Result<OrderView> create(@Valid @RequestBody NewOrder input) {return Api.Result.ok(service.create(input));}
    @GetMapping public Api.Result<Api.Page<OrderView>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size,@RequestParam(required=false)OrderStatus status) {return Api.Result.ok(service.list(page,size,null,status,false));}
    @GetMapping("/{orderNo}") public Api.Result<OrderView> detail(@PathVariable String orderNo) {return Api.Result.ok(service.detail(orderNo));}
    @PostMapping("/{orderNo}/mock-pay") public Api.Result<OrderView> pay(@PathVariable String orderNo) {return Api.Result.ok(service.pay(orderNo));}
    @PostMapping("/{orderNo}/cancel") public Api.Result<OrderView> cancel(@PathVariable String orderNo) {return Api.Result.ok(service.cancel(orderNo,false));}
}
