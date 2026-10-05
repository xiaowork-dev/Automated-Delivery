package com.xiaowork.autodelivery.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaowork.autodelivery.common.*;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.infra.RedisSupport;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.*;
import com.xiaowork.autodelivery.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
@Service @RequiredArgsConstructor
public class AdminService {
    private final ProductMapper productMapper;
    private final ProductService products;
    private final CodeMapper codes;
    private final UserMapper users;
    private final OrderMapper orders;
    private final LogMapper logs;
    private final AuditService audit;
    private final RedisSupport cache;
    @Transactional public ImportResult importCodes(ImportCodes input) {
        products.require(input.productId());
        if(input.expiredAt()!=null && !input.expiredAt().isAfter(Times.now()))throw BusinessException.invalid("有效期必须晚于当前时间");
        String[] lines=input.codes().split("\\R",-1);
        if(lines.length>10000) throw BusinessException.invalid("每次最多导入10000行");
        int success=0,duplicate=0,invalid=0;var seen=new HashSet<String>();
        for(String line:lines) {
            String value=line.trim();if(value.isEmpty())continue;
            if(!value.matches("[A-Za-z0-9_-]{4,128}")) { invalid++;continue; }
            if(!seen.add(value) || codes.byCode(value)!=null) { duplicate++;continue; }
            var code=new Code();code.setProductId(input.productId());code.setCode(value);code.setStatus(CodeStatus.UNUSED);code.setExpiredAt(input.expiredAt());code.setCreatedAt(Times.now());
            try { codes.insert(code);success++; } catch(DuplicateKeyException ex) { duplicate++; }
        }
        audit.record("IMPORT_CODES","PRODUCT",input.productId(),"成功="+success+" 重复="+duplicate+" 格式错误="+invalid);cache.evictProduct(input.productId());
        return new ImportResult(success,duplicate,invalid);
    }
    public static String mask(String code) { return code.length()<=8?code.substring(0,1)+"****"+code.substring(code.length()-1):code.substring(0,4)+"****"+code.substring(code.length()-4); }
    public Api.Page<AdminCodeView> codes(int page,int size,Long productId,CodeStatus status) {
        var q=new QueryWrapper<Code>();if(productId!=null)q.eq("product_id",productId);if(status!=null)q.eq("status",status.name());
        return Pages.of(codes,q,page,size,c->{var p=products.require(c.getProductId());var o=c.getOrderId()==null?null:orders.selectById(c.getOrderId());return new AdminCodeView(c.getId(),c.getProductId(),p.getName(),mask(c.getCode()),c.getStatus(),c.getOrderId(),o==null?null:o.getOrderNo(),c.getAssignedUserId(),c.getAssignedAt(),c.getUsedAt(),c.getExpiredAt(),c.getCreatedAt());});
    }
    @Transactional public Void disable(Long id,DisableCode input) {
        var initial=codes.selectById(id);if(initial==null)throw BusinessException.missing("兑换码");
        if(initial.getOrderId()!=null)orders.lockById(initial.getOrderId());
        var c=codes.lockById(id);
        if(c.getStatus()!=CodeStatus.UNUSED && c.getStatus()!=CodeStatus.ASSIGNED)throw BusinessException.conflict("CODE_STATUS_INVALID","只有未使用且有效的兑换码可以作废");
        c.setStatus(CodeStatus.DISABLED);codes.updateById(c);audit.record("DISABLE_CODE","CODE",id,input.reason());cache.evictProduct(c.getProductId());return null;
    }
    public Api.Page<UserView> users(int page,int size,String keyword) { var q=new QueryWrapper<User>();if(Pages.text(keyword))q.like("username",keyword.trim());return Pages.of(users,q,page,size,AuthService::view); }
    @Transactional public UserView userStatus(Long id,UserStatus input) {
        var u=users.selectById(id);if(u==null)throw BusinessException.missing("用户");
        if(u.getRole()==UserRole.ADMIN && input.status()==0)throw BusinessException.conflict("ADMIN_PROTECTED","管理员账号不能在此停用");
        u.setStatus(input.status());u.setUpdatedAt(Times.now());users.updateById(u);audit.record("UPDATE_USER_STATUS","USER",id,"status="+input.status());return AuthService.view(u);
    }
    public Api.Page<OperationLog> logs(int page,int size) { return Pages.of(logs,new QueryWrapper<>(),page,size,l->l); }
    public Dashboard dashboard() {
        var now=Times.now();long count=productMapper.selectCount(null);
        long today=orders.selectCount(new QueryWrapper<Order>().ge("created_at",now.toLocalDate().atStartOfDay()).lt("created_at",now.toLocalDate().plusDays(1).atStartOfDay()));
        long available=codes.selectCount(new QueryWrapper<Code>().eq("status",CodeStatus.UNUSED.name()).and(q->q.isNull("expired_at").or().gt("expired_at",now)));
        long delivered=orders.selectCount(new QueryWrapper<Order>().in("status",OrderStatus.DELIVERED.name(),OrderStatus.COMPLETED.name()));
        var low=productMapper.selectList(new QueryWrapper<Product>().eq("status",ProductStatus.ON_SALE.name())).stream().map(products::view).filter(p->p.stock()<=5).limit(20).toList();
        return new Dashboard(count,today,available,delivered,low);
    }
}
