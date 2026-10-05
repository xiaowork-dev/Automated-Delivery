package com.xiaowork.autodelivery;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaowork.autodelivery.common.BusinessException;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.common.Times;
import com.xiaowork.autodelivery.infra.ExpiryScheduler;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.*;
import com.xiaowork.autodelivery.security.Actor;
import com.xiaowork.autodelivery.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real transactions and row locks on H2 in MySQL mode; real MySQL HTTP races run separately. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:deliverytest;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "app.jwt-secret=integration-tests-only-key-at-least-32-bytes","app.admin-password=Integration_Admin_123",
    "app.admin-username=integration_admin","app.seed-demo=false","app.redis-enabled=false",
    "app.expiry-initial-delay-ms=3600000","logging.file.name=target/test-logs/application.log"
})
@AutoConfigureMockMvc
class BusinessIntegrationTest {
    @Autowired ProductService products;
    @Autowired OrderService orders;
    @Autowired AdminService admin;
    @Autowired RedeemService redeem;
    @Autowired AuthService auth;
    @Autowired UserMapper users;
    @Autowired CodeMapper codes;
    @Autowired OrderMapper orderMapper;
    @Autowired RecordMapper records;
    @Autowired ExpiryScheduler scheduler;
    @Autowired MockMvc mvc;
    @MockitoSpyBean DeliveryMapper deliveries;
    Actor buyer,administrator,other;
    @BeforeEach void setup() {
        var a=users.byUsername("integration_admin");administrator=new Actor(a.getId(),a.getUsername(),a.getRole());
        var u=new User();u.setUsername("buyer_"+UUID.randomUUID().toString().replace("-","").substring(0,12));u.setPasswordHash("unused-in-service-test");u.setRole(UserRole.USER);u.setStatus(1);u.setCreatedAt(Times.now());u.setUpdatedAt(Times.now());users.insert(u);buyer=new Actor(u.getId(),u.getUsername(),u.getRole());
        var b=new User();b.setUsername("other_"+UUID.randomUUID().toString().replace("-","").substring(0,12));b.setPasswordHash("unused-in-service-test");b.setRole(UserRole.USER);b.setStatus(1);b.setCreatedAt(Times.now());b.setUpdatedAt(Times.now());users.insert(b);other=new Actor(b.getId(),b.getUsername(),b.getRole());
        actor(administrator);
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();reset(deliveries);}
    static void actor(Actor actor) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));}
    ProductView product(int stock) {
        actor(administrator);var p=products.save(null,new ProductInput("测试商品","模拟商品",new BigDecimal("19.90"),"","描述",ProductStatus.ON_SALE));
        if(stock>0)admin.importCodes(new ImportCodes(p.id(),java.util.stream.IntStream.range(0,stock).mapToObj(i->"TEST-"+UUID.randomUUID()).reduce((a,b)->a+"\n"+b).orElseThrow(),null));
        return p;
    }
    OrderView order(Long product) {actor(buyer);return orders.create(new NewOrder(product));}
    @Test void paymentIsIdempotentAndRedeemIsExactlyOnce() {
        var p=product(2);var o=order(p.id());var paid=orders.pay(o.orderNo());
        for(int i=0;i<10;i++)assertThat(orders.pay(o.orderNo()).redeemCode().id()).isEqualTo(paid.redeemCode().id());
        assertThat(codes.stock(p.id(),Times.now())).isEqualTo(1);
        actor(other);assertThatThrownBy(()->orders.detail(o.orderNo())).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(()->redeem.redeem(new RedeemInput(paid.redeemCode().code()),"127.0.0.1")).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
        actor(buyer);assertThat(redeem.redeem(new RedeemInput(paid.redeemCode().code()),"127.0.0.1").status()).isEqualTo(OrderStatus.COMPLETED);
        assertThatThrownBy(()->redeem.redeem(new RedeemInput(paid.redeemCode().code()),"127.0.0.1")).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CODE_ALREADY_USED");
        assertThat(records.selectCount(new QueryWrapper<RedeemRecord>().eq("redeem_code_id",paid.redeemCode().id()))).isEqualTo(1);
        assertThat(orders.pay(o.orderNo()).status()).isEqualTo(OrderStatus.COMPLETED);
    }
    @Test void deliveryFailureRollsBackCodeOrderAndLogs() {
        var p=product(1);var o=order(p.id());
        doThrow(new IllegalStateException("injected delivery-log failure")).when(deliveries).insert(any(DeliveryLog.class));
        assertThatThrownBy(()->orders.pay(o.orderNo())).isInstanceOf(IllegalStateException.class);
        assertThat(orderMapper.byNumber(o.orderNo()).getStatus()).isEqualTo(OrderStatus.WAIT_PAY);
        assertThat(orderMapper.byNumber(o.orderNo()).getPaidAt()).isNull();
        assertThat(codes.byOrder(o.id())).isNull();assertThat(codes.stock(p.id(),Times.now())).isEqualTo(1);
        assertThat(deliveries.selectCount(new QueryWrapper<DeliveryLog>().eq("order_id",o.id()))).isZero();
        reset(deliveries);assertThat(orders.pay(o.orderNo()).status()).isEqualTo(OrderStatus.DELIVERED);
    }
    @Test void oneStockFiveConcurrentOrdersHasOneDelivery() throws Exception {
        var p=product(1);var pending=new ArrayList<String>();for(int i=0;i<5;i++)pending.add(order(p.id()).orderNo());
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(5);
        try {
            List<Future<String>> futures=pending.stream().map(no->pool.submit(()->{actor(buyer);gate.await();try{return orders.pay(no).status().name();}catch(BusinessException ex){return ex.code;}finally{SecurityContextHolder.clearContext();}})).toList();
            gate.countDown();var results=new ArrayList<String>();for(var f:futures)results.add(f.get(30,TimeUnit.SECONDS));
            assertThat(results).containsOnly("DELIVERED","OUT_OF_STOCK");assertThat(results.stream().filter("DELIVERED"::equals).count()).isEqualTo(1);
            assertThat(codes.selectCount(new QueryWrapper<Code>().eq("product_id",p.id()).eq("status","ASSIGNED"))).isEqualTo(1);
            assertThat(pending.stream().map(orderMapper::byNumber).filter(o->o.getStatus()==OrderStatus.WAIT_PAY).count()).isEqualTo(4);
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentSameOrderKeepsSingleBinding() throws Exception {
        var p=product(8);var o=order(p.id());var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=java.util.stream.IntStream.range(0,8).mapToObj(i->pool.submit(()->{actor(buyer);gate.await();try{return orders.pay(o.orderNo()).redeemCode().id();}finally{SecurityContextHolder.clearContext();}})).toList();gate.countDown();
            var ids=new HashSet<Long>();for(var f:futures)ids.add(f.get(30,TimeUnit.SECONDS));assertThat(ids).hasSize(1);assertThat(codes.stock(p.id(),Times.now())).isEqualTo(7);
        }finally{pool.shutdownNow();}
    }
    @Test void snapshotsImportStateAndExpiryAreEnforced() {
        var p=product(1);actor(administrator);var code=codes.selectList(new QueryWrapper<Code>().eq("product_id",p.id())).getFirst().getCode();
        var imported=admin.importCodes(new ImportCodes(p.id(),"  "+code+"  \n\n"+code+"\n"+"x".repeat(129),null));assertThat(imported).isEqualTo(new ImportResult(0,2,1));
        var o=order(p.id());actor(administrator);products.save(p.id(),new ProductInput("改名","",new BigDecimal("29.90"),"","",ProductStatus.OFF_SHELF));
        actor(buyer);var snapshot=orders.detail(o.orderNo());assertThat(snapshot.productName()).isEqualTo(p.name());assertThat(snapshot.amount()).isEqualByComparingTo("19.90");
        assertThatThrownBy(()->orders.create(new NewOrder(p.id()))).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("PRODUCT_OFF_SHELF");
        var db=orderMapper.byNumber(o.orderNo());db.setExpireAt(Times.now().minusMinutes(1));orderMapper.updateById(db);assertThatThrownBy(()->orders.pay(o.orderNo())).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ORDER_EXPIRED");
        scheduler.expire();scheduler.expire();assertThat(orderMapper.byNumber(o.orderNo()).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        var c=codes.byCode(code);c.setExpiredAt(Times.now().minusDays(1));codes.updateById(c);scheduler.expire();assertThat(codes.byCode(code).getStatus()).isEqualTo(CodeStatus.EXPIRED);
    }
    @Test void assignedExpiredCodeCannotRedeemAndUsedCodeCannotDisable() {
        var p=product(2);var o=order(p.id());var paid=orders.pay(o.orderNo());var c=codes.selectById(paid.redeemCode().id());c.setExpiredAt(Times.now().minusMinutes(1));codes.updateById(c);
        assertThatThrownBy(()->redeem.redeem(new RedeemInput(c.getCode()),"127.0.0.1")).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CODE_EXPIRED");
        assertThat(records.selectCount(new QueryWrapper<RedeemRecord>().eq("redeem_code_id",c.getId()))).isZero();
        var second=order(p.id());var delivered=orders.pay(second.orderNo());redeem.redeem(new RedeemInput(delivered.redeemCode().code()),"127.0.0.1");actor(administrator);
        assertThatThrownBy(()->admin.disable(delivered.redeemCode().id(),new DisableCode("测试"))).isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CODE_STATUS_INVALID");
    }
    @Test void securityRejectsAnonymousNonAdminTamperedAndRevokedTokens() throws Exception {
        // Service fixtures set a thread-local actor; HTTP tests must start without that trusted fixture context.
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        String username="http_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var logged=auth.register(new Credentials(username,"Password_12345"));
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization","Bearer "+logged.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+logged.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username));
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+logged.token()+"x")).andExpect(status().isUnauthorized());
        actor(new Actor(logged.user().id(),username,UserRole.USER));auth.changePassword(new PasswordChange("Password_12345","New_Password_123"));
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+logged.token())).andExpect(status().isUnauthorized());
        var refreshed=auth.login(new Credentials(username,"New_Password_123"));actor(administrator);admin.userStatus(logged.user().id(),new UserStatus(0));
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+refreshed.token())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"username\":\"valid_name\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
    }
}
