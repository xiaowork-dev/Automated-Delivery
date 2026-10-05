package com.xiaowork.autodelivery;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaowork.autodelivery.common.BusinessException;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.common.Times;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.*;
import com.xiaowork.autodelivery.security.Actor;
import com.xiaowork.autodelivery.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in MySQL driver/real InnoDB tests. Use a dedicated test schema; no existing data is deleted. */
@EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL",matches=".+")
@SpringBootTest(properties={
    "spring.datasource.url=${TEST_MYSQL_URL}","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.username=${TEST_MYSQL_USER:root}","spring.datasource.password=${TEST_MYSQL_PASSWORD:}",
    "app.jwt-secret=mysql-integration-only-key-at-least-32-bytes","app.admin-username=mysql_test_admin",
    "app.admin-password=MySql_Test_Admin_123","app.seed-demo=false","app.redis-enabled=false",
    "app.scheduler-enabled=false","logging.file.name=target/test-logs/mysql.log"
})
class MySqlConcurrencyTest {
    @Autowired ProductService products;
    @Autowired OrderService orders;
    @Autowired AdminService admin;
    @Autowired UserMapper users;
    @Autowired CodeMapper codes;
    @Autowired OrderMapper orderMapper;
    @Autowired DeliveryMapper deliveries;
    @Autowired JdbcTemplate jdbc;
    Actor buyer,administrator;
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT VERSION()",String.class)).contains("8.");
        var a=users.byUsername("mysql_test_admin");administrator=new Actor(a.getId(),a.getUsername(),a.getRole());
        var u=new User();u.setUsername("mysql_"+UUID.randomUUID().toString().replace("-","").substring(0,14));u.setPasswordHash("unused-in-service-tests");u.setRole(UserRole.USER);u.setStatus(1);u.setCreatedAt(Times.now());u.setUpdatedAt(Times.now());users.insert(u);buyer=new Actor(u.getId(),u.getUsername(),u.getRole());
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    static void actor(Actor a) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(a,null,List.of()));}
    List<String> pending(int stock,int count) {
        actor(administrator);var p=products.save(null,new ProductInput("MySQL 并发回归","真实 InnoDB",new BigDecimal("19.90"),"","",ProductStatus.ON_SALE));
        admin.importCodes(new ImportCodes(p.id(),java.util.stream.IntStream.range(0,stock).mapToObj(i->"MYSQL-"+UUID.randomUUID()).reduce((a,b)->a+"\n"+b).orElseThrow(),null));
        actor(buyer);var numbers=new ArrayList<String>();for(int i=0;i<count;i++)numbers.add(orders.create(new NewOrder(p.id())).orderNo());return numbers;
    }
    List<String> payTogether(List<String> numbers) throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(numbers.size());
        try {
            var tasks=numbers.stream().map(no->pool.submit(()->{actor(buyer);start.await();try{return orders.pay(no).status().name();}catch(BusinessException ex){return ex.code;}finally{SecurityContextHolder.clearContext();}})).toList();
            start.countDown();var results=new ArrayList<String>();for(var task:tasks)results.add(task.get(30,TimeUnit.SECONDS));return results;
        } finally {pool.shutdownNow();}
    }
    @Test void twentySingleStockRacesHaveExactlyOneWinnerAndOutOfStockLosers() throws Exception {
        for(int batch=0;batch<20;batch++) {
            var numbers=pending(1,5);var results=payTogether(numbers);
            assertThat(results).containsOnly("DELIVERED","OUT_OF_STOCK");assertThat(Collections.frequency(results,"DELIVERED")).isEqualTo(1);
            var persisted=numbers.stream().map(orderMapper::byNumber).toList();assertThat(persisted.stream().filter(o->o.getStatus()==OrderStatus.WAIT_PAY).count()).isEqualTo(4);
            assertThat(persisted.stream().filter(o->o.getPaidAt()!=null).count()).isEqualTo(1);
            var ids=persisted.stream().map(Order::getId).toList();assertThat(deliveries.selectCount(new QueryWrapper<DeliveryLog>().in("order_id",ids))).isEqualTo(1);
        }
    }
    @Test void twentyOrdersCompetingForTenCodesDeliverExactlyTen() throws Exception {
        var numbers=pending(10,20);var results=payTogether(numbers);assertThat(results).containsOnly("DELIVERED","OUT_OF_STOCK");assertThat(Collections.frequency(results,"DELIVERED")).isEqualTo(10);
        var ids=numbers.stream().map(orderMapper::byNumber).map(Order::getId).toList();assertThat(codes.selectCount(new QueryWrapper<Code>().in("order_id",ids))).isEqualTo(10);
    }
    @Test void twentyConcurrentRetriesOfSameOrderConsumeOnlyOneCode() throws Exception {
        var numbers=pending(5,1);var results=payTogether(Collections.nCopies(20,numbers.getFirst()));assertThat(results).containsOnly("DELIVERED");
        var order=orderMapper.byNumber(numbers.getFirst());assertThat(codes.selectCount(new QueryWrapper<Code>().eq("order_id",order.getId()))).isEqualTo(1);assertThat(codes.stock(codes.byOrder(order.getId()).getProductId(),Times.now())).isEqualTo(4);
        assertThat(deliveries.selectCount(new QueryWrapper<DeliveryLog>().eq("order_id",order.getId()))).isEqualTo(1);
    }
    @Test void databaseFailureAfterAssignmentRollsBackWholePayment() {
        var numbers=pending(1,1);String number=numbers.getFirst();var order=orderMapper.byNumber(number);
        // Dedicated test schema only: inject a database failure after code assignment and order mutation.
        jdbc.execute("CREATE TRIGGER fail_delivery_test BEFORE INSERT ON delivery_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected test delivery failure'");
        try {
            assertThatThrownBy(()->orders.pay(number)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            var unchanged=orderMapper.byNumber(number);assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.WAIT_PAY);assertThat(unchanged.getPaidAt()).isNull();assertThat(unchanged.getDeliveredAt()).isNull();
            assertThat(codes.byOrder(order.getId())).isNull();assertThat(deliveries.selectCount(new QueryWrapper<DeliveryLog>().eq("order_id",order.getId()))).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_delivery_test"); }
        assertThat(orders.pay(number).status()).isEqualTo(OrderStatus.DELIVERED);
    }
}
