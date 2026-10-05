package com.xiaowork.autodelivery.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xiaowork.autodelivery.common.*;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.infra.RedisSupport;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.*;
import com.xiaowork.autodelivery.security.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
@Slf4j @Service @RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orders;
    private final ItemMapper items;
    private final CodeMapper codes;
    private final DeliveryMapper deliveries;
    private final ProductService products;
    private final AuditService audit;
    private final RedisSupport cache;
    @Value("${app.order-expiry-minutes:30}") private long expiryMinutes;
    public OrderView view(Order o) {
        var item=items.byOrder(o.getId());var code=codes.byOrder(o.getId());
        CodeView codeView=null;
        if(code!=null && (o.getStatus()==OrderStatus.DELIVERED || o.getStatus()==OrderStatus.COMPLETED)) codeView=new CodeView(code.getId(),code.getCode(),code.getStatus(),code.getExpiredAt(),code.getUsedAt());
        return new OrderView(o.getId(),o.getOrderNo(),o.getUserId(),item.getProductId(),item.getProductName(),item.getUnitPrice(),o.getTotalAmount(),o.getStatus(),o.getCreatedAt(),o.getExpireAt(),o.getPaidAt(),o.getDeliveredAt(),codeView);
    }
    @Transactional public OrderView create(NewOrder input) {
        var p=products.require(input.productId());
        if(p.getStatus()!=ProductStatus.ON_SALE) throw BusinessException.conflict("PRODUCT_OFF_SHELF","商品已下架");
        if(codes.stock(p.getId(),Times.now())<1) throw BusinessException.conflict("OUT_OF_STOCK","暂无可用库存");
        var now=Times.now();var o=new Order();o.setOrderNo(now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+UUID.randomUUID().toString().replace("-","").substring(0,20));o.setUserId(Actor.current().id());o.setTotalAmount(p.getPrice());o.setStatus(OrderStatus.WAIT_PAY);o.setPayType("MOCK");o.setVersion(0);o.setExpireAt(now.plusMinutes(expiryMinutes));o.setCreatedAt(now);o.setUpdatedAt(now);orders.insert(o);
        var item=new OrderItem();item.setOrderId(o.getId());item.setProductId(p.getId());item.setProductName(p.getName());item.setUnitPrice(p.getPrice());item.setQuantity(1);item.setSubtotal(p.getPrice());items.insert(item);
        return view(o);
    }
    public Order require(String number,boolean lock) { var o=lock?orders.lockByNumber(number):orders.byNumber(number);if(o==null) throw BusinessException.missing("订单");Actor.current().requireOwner(o.getUserId());return o; }
    public OrderView detail(String number) { return view(require(number,false)); }
    public Api.Page<OrderView> list(int page,int size,String keyword,OrderStatus status,boolean admin) {
        var q=new QueryWrapper<Order>();if(!admin) q.eq("user_id",Actor.current().id());if(status!=null)q.eq("status",status.name());if(Pages.text(keyword))q.like("order_no",keyword.trim());return Pages.of(orders,q,page,size,this::view);
    }
    /** One transaction: order lock -> stock row lock -> conditional assignment -> order + delivery log. */
    // InnoDB READ COMMITTED avoids range/gap-lock conversion cycles between contending stock scans.
    // Exact order and code rows remain exclusively locked until commit; unique constraints remain unchanged.
    @Transactional(isolation = Isolation.READ_COMMITTED) public OrderView pay(String number) {
        var o=require(number,true);
        if(o.getStatus()==OrderStatus.DELIVERED || o.getStatus()==OrderStatus.COMPLETED) return view(o);
        if(o.getStatus()!=OrderStatus.WAIT_PAY) throw BusinessException.conflict("ORDER_STATUS_INVALID","订单当前状态不能支付");
        var now=Times.now();if(!o.getExpireAt().isAfter(now)) throw BusinessException.conflict("ORDER_EXPIRED","订单已超时，请重新下单");
        var item=items.byOrder(o.getId());var code=codes.lockAvailable(item.getProductId(),now);
        if(code==null || codes.assign(code.getId(),o.getId(),o.getUserId(),now)!=1) throw BusinessException.conflict("OUT_OF_STOCK","库存刚刚发生变化，请重新下单");
        int updated=orders.update(null,new UpdateWrapper<Order>().eq("id",o.getId()).eq("status",OrderStatus.WAIT_PAY.name()).set("status",OrderStatus.DELIVERED.name()).set("paid_at",now).set("delivered_at",now).set("updated_at",now).setSql("version=version+1"));
        if(updated!=1) throw BusinessException.conflict("ORDER_STATUS_INVALID","订单状态已变更");
        var entry=new DeliveryLog();entry.setOrderId(o.getId());entry.setCodeId(code.getId());entry.setUserId(o.getUserId());entry.setProductId(item.getProductId());entry.setRequestId(MDC.get("requestId"));entry.setResult("SUCCESS");entry.setCreatedAt(now);deliveries.insert(entry);
        cache.evictProduct(item.getProductId());log.info("delivery requestId={} orderNo={} userId={} productId={} codeId={}",MDC.get("requestId"),o.getOrderNo(),o.getUserId(),item.getProductId(),code.getId());
        return view(orders.selectById(o.getId()));
    }
    @Transactional public OrderView cancel(String number,boolean adminClose) {
        var o=require(number,true);if(o.getStatus()!=OrderStatus.WAIT_PAY) throw BusinessException.conflict("ORDER_STATUS_INVALID","仅待支付订单可以关闭");
        o.setStatus(o.getExpireAt().isAfter(Times.now())?OrderStatus.CANCELLED:OrderStatus.EXPIRED);o.setUpdatedAt(Times.now());o.setVersion(o.getVersion()+1);orders.updateById(o);
        if(adminClose)audit.record("CLOSE_ORDER","ORDER",o.getId(),"管理员关闭待支付订单");return view(o);
    }
}
