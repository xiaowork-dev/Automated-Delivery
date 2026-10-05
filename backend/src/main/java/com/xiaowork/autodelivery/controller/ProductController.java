package com.xiaowork.autodelivery.controller;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.model.Dtos.ProductView;
import com.xiaowork.autodelivery.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    @GetMapping public Api.Result<Api.Page<ProductView>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int size,@RequestParam(required=false)String keyword) {return Api.Result.ok(service.list(page,size,keyword,null,false));}
    @GetMapping("/{id}") public Api.Result<ProductView> detail(@PathVariable Long id) {return Api.Result.ok(service.detail(id));}
}
