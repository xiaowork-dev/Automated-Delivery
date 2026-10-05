package com.xiaowork.autodelivery.infra;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.common.Times;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Entities.*;
import com.xiaowork.autodelivery.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;
@Slf4j @Service @RequiredArgsConstructor
public class BootstrapService {
    private final UserMapper users;
    private final ProductMapper products;
    private final CodeMapper codes;
    private final PasswordEncoder passwords;
    @Value("${app.admin-username:admin}") private String username;
    @Value("${app.admin-password:}") private String password;
    @Value("${app.seed-demo:false}") private boolean demo;
    @Transactional public void initialize() {
        if(!password.isBlank()) {
            if(!username.matches("[a-zA-Z0-9_-]{3,50}") || password.length()<8)throw new IllegalStateException("Invalid bootstrap admin username or password length");
            AuthService.validatePassword(password);
            var existing=users.byUsername(username);
            if(existing==null) {var u=new User();u.setUsername(username);u.setPasswordHash(passwords.encode(password));u.setRole(UserRole.ADMIN);u.setStatus(1);u.setCreatedAt(Times.now());u.setUpdatedAt(Times.now());users.insert(u);log.info("Bootstrap administrator created");}
            else if(existing.getRole()!=UserRole.ADMIN)throw new IllegalStateException("Bootstrap admin username already belongs to a non-admin user");
        } else if(users.selectCount(new QueryWrapper<User>().eq("role",UserRole.ADMIN.name()))==0) {log.warn("No administrator exists; supply ADMIN_PASSWORD to bootstrap one");}
        if(demo && products.selectCount(null)==0) {
            seedProduct("云端灵感手册","30 个创意练习，开启你的下一次创作",new BigDecimal("19.90"),"这是一份模拟数字商品，仅用于体验自助交付。兑换后将在演示系统中标记完成。",24);
            seedProduct("效率工具体验包","把日常任务整理得更清晰",new BigDecimal("39.90"),"模拟工具兑换权益，体验从下单、演示支付到兑换的完整流程。",12);
            seedProduct("设计入门素材集","让每一个想法都找到表达方式",new BigDecimal("9.90"),"用于项目演示的合法虚拟素材商品，不收取真实费用。",3);
        }
    }
    private void seedProduct(String name,String subtitle,BigDecimal price,String description,int stock) {
        var p=new Product();p.setName(name);p.setSubtitle(subtitle);p.setPrice(price);p.setDescription(description);p.setStatus(ProductStatus.ON_SALE);p.setCreatedAt(Times.now());p.setUpdatedAt(Times.now());products.insert(p);
        var random=new SecureRandom();for(int i=0;i<stock;i++){byte[] bytes=new byte[16];random.nextBytes(bytes);var c=new Code();c.setProductId(p.getId());c.setCode("DEMO-"+HexFormat.of().formatHex(bytes));c.setStatus(CodeStatus.UNUSED);c.setCreatedAt(Times.now());codes.insert(c);}
    }
}
