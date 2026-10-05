package com.xiaowork.autodelivery.controller;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.OperationLog;
import com.xiaowork.autodelivery.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin") @RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    private final ProductService products;
    private final OrderService orders;
    @GetMapping("/dashboard") public Api.Result<Dashboard> dashboard() {return Api.Result.ok(admin.dashboard());}
    @GetMapping("/products") public Api.Result<Api.Page<ProductView>> products(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size,@RequestParam(required=false)String keyword,@RequestParam(required=false)ProductStatus status) {return Api.Result.ok(products.list(page,size,keyword,status,true));}
    @PostMapping("/products") public Api.Result<ProductView> create(@Valid @RequestBody ProductInput input) {return Api.Result.ok(products.save(null,input));}
    @PutMapping("/products/{id}") public Api.Result<ProductView> update(@PathVariable Long id,@Valid @RequestBody ProductInput input) {return Api.Result.ok(products.save(id,input));}
    @PostMapping("/redeem-codes/import") public Api.Result<ImportResult> importCodes(@Valid @RequestBody ImportCodes input) {return Api.Result.ok(admin.importCodes(input));}
    @GetMapping("/redeem-codes") public Api.Result<Api.Page<AdminCodeView>> codes(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size,@RequestParam(required=false)Long productId,@RequestParam(required=false)CodeStatus status) {return Api.Result.ok(admin.codes(page,size,productId,status));}
    @PostMapping("/redeem-codes/{id}/disable") public Api.Result<Void> disable(@PathVariable Long id,@Valid @RequestBody DisableCode input) {return Api.Result.ok(admin.disable(id,input));}
    @GetMapping("/orders") public Api.Result<Api.Page<OrderView>> orders(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size,@RequestParam(required=false)String keyword,@RequestParam(required=false)OrderStatus status) {return Api.Result.ok(orders.list(page,size,keyword,status,true));}
    @GetMapping("/orders/{orderNo}") public Api.Result<OrderView> order(@PathVariable String orderNo) {return Api.Result.ok(orders.detail(orderNo));}
    @PostMapping("/orders/{orderNo}/close") public Api.Result<OrderView> close(@PathVariable String orderNo) {return Api.Result.ok(orders.cancel(orderNo,true));}
    @GetMapping("/users") public Api.Result<Api.Page<UserView>> users(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size,@RequestParam(required=false)String keyword) {return Api.Result.ok(admin.users(page,size,keyword));}
    @PutMapping("/users/{id}/status") public Api.Result<UserView> userStatus(@PathVariable Long id,@Valid @RequestBody UserStatus input) {return Api.Result.ok(admin.userStatus(id,input));}
    @GetMapping("/logs") public Api.Result<Api.Page<OperationLog>> logs(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size) {return Api.Result.ok(admin.logs(page,size));}
}
