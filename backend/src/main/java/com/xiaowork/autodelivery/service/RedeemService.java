package com.xiaowork.autodelivery.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiaowork.autodelivery.common.*;
import com.xiaowork.autodelivery.common.States.*;
import com.xiaowork.autodelivery.mapper.*;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.*;
import com.xiaowork.autodelivery.security.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Slf4j @Service @RequiredArgsConstructor
public class RedeemService {
    private final CodeMapper codes;
    private final OrderMapper orders;
    private final ItemMapper items;
    private final RecordMapper records;
    @Transactional public RedeemView redeem(RedeemInput input,String ip) {
        var initial=codes.byCode(input.code().trim());
        if(initial==null) throw new BusinessException("CODE_INVALID",404,"兑换码不存在");
        var actor=Actor.current();
        if(initial.getAssignedUserId()==null || !initial.getAssignedUserId().equals(actor.id())) throw BusinessException.forbidden();
        // Follow the same lock order as payment and assigned-code administration, preventing lock cycles.
        var order=orders.lockById(initial.getOrderId());var code=codes.lockById(initial.getId());
        if(!actor.id().equals(code.getAssignedUserId())) throw BusinessException.forbidden();
        if(code.getStatus()==CodeStatus.USED) throw BusinessException.conflict("CODE_ALREADY_USED","兑换码已使用");
        if(code.getExpiredAt()!=null && !code.getExpiredAt().isAfter(Times.now())) throw BusinessException.conflict("CODE_EXPIRED","兑换码已过期");
        if(code.getStatus()!=CodeStatus.ASSIGNED || order.getStatus()!=OrderStatus.DELIVERED) throw BusinessException.conflict("CODE_STATUS_INVALID","兑换码当前状态不能兑换");
        var now=Times.now();if(codes.use(code.getId(),actor.id(),now)!=1)throw BusinessException.conflict("CODE_STATUS_INVALID","兑换码状态已发生变化");
        var record=new RedeemRecord();record.setRedeemCodeId(code.getId());record.setOrderId(order.getId());record.setUserId(actor.id());record.setResult("SUCCESS");record.setReason("自助兑换成功");record.setRequestId(MDC.get("requestId"));record.setIp(ip);record.setCreatedAt(now);records.insert(record);
        order.setStatus(OrderStatus.COMPLETED);order.setVersion(order.getVersion()+1);order.setUpdatedAt(now);orders.updateById(order);
        var item=items.byOrder(order.getId());log.info("redeem requestId={} orderNo={} userId={} codeId={}",MDC.get("requestId"),order.getOrderNo(),actor.id(),code.getId());
        return new RedeemView(order.getOrderNo(),item.getProductName(),order.getStatus(),now,"兑换成功，订单已完成");
    }
    public Api.Page<RedeemRecordView> records(int page,int size) {
        return Pages.of(records,new QueryWrapper<RedeemRecord>().eq("user_id",Actor.current().id()),page,size,r->{var order=orders.selectById(r.getOrderId());var item=items.byOrder(r.getOrderId());return new RedeemRecordView(r.getId(),order.getOrderNo(),item.getProductName(),r.getResult(),r.getCreatedAt());});
    }
}
