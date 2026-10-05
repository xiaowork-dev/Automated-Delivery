package com.xiaowork.autodelivery.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaowork.autodelivery.common.*;
import com.xiaowork.autodelivery.common.States.ProductStatus;
import com.xiaowork.autodelivery.infra.RedisSupport;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class ProductService {
    private final ProductMapper products;
    private final CodeMapper codes;
    private final AuditService audit;
    private final RedisSupport cache;
    public Product require(Long id) { var p=products.selectById(id);if(p==null) throw BusinessException.missing("商品");return p; }
    public ProductView view(Product p) {
        long stock=codes.stock(p.getId(),Times.now());
        return new ProductView(p.getId(),p.getName(),p.getSubtitle(),p.getPrice(),p.getCoverUrl(),p.getDescription(),p.getStatus(),stock,stock==0?"缺货":stock<=5?"库存紧张":"有货");
    }
    public ProductView detail(Long id) { return cache.product(id,()->{var p=require(id);if(p.getStatus()!=ProductStatus.ON_SALE) throw BusinessException.missing("商品");return view(p);}); }
    public Api.Page<ProductView> list(int page,int size,String keyword,ProductStatus status,boolean admin) {
        var q=new QueryWrapper<Product>();
        if(Pages.text(keyword)) q.like("name",keyword.trim());
        if(!admin) q.eq("status",ProductStatus.ON_SALE.name());else if(status!=null) q.eq("status",status.name());
        return Pages.of(products,q,page,size,this::view);
    }
    @Transactional public ProductView save(Long id,ProductInput input) {
        if(Pages.text(input.coverUrl()) && !(input.coverUrl().startsWith("https://") || input.coverUrl().startsWith("http://") || input.coverUrl().startsWith("/"))) throw BusinessException.invalid("封面地址必须为 HTTP(S) 地址或本站路径");
        var p=id==null?new Product():require(id);
        p.setName(input.name().trim());p.setSubtitle(input.subtitle());p.setPrice(input.price());p.setCoverUrl(input.coverUrl());p.setDescription(input.description());
        p.setStatus(input.status()==null?(id==null?ProductStatus.OFF_SHELF:p.getStatus()):input.status());p.setUpdatedAt(Times.now());
        if(id==null) { p.setCreatedAt(Times.now());products.insert(p); } else products.updateById(p);
        audit.record(id==null?"CREATE_PRODUCT":"UPDATE_PRODUCT","PRODUCT",p.getId(),"商品信息维护 status="+p.getStatus());cache.evictProduct(p.getId());
        return view(p);
    }
}
