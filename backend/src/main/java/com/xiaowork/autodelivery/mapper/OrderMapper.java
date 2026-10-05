package com.xiaowork.autodelivery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaowork.autodelivery.model.Entities.Order;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
public interface OrderMapper extends BaseMapper<Order> {
    @Select("SELECT * FROM order_info WHERE order_no=#{orderNo}") Order byNumber(String orderNo);
    @Select("SELECT * FROM order_info WHERE order_no=#{orderNo} FOR UPDATE") Order lockByNumber(String orderNo);
    @Select("SELECT * FROM order_info WHERE id=#{id} FOR UPDATE") Order lockById(Long id);
    @Update("UPDATE order_info SET status='EXPIRED',version=version+1,updated_at=#{now} WHERE status='WAIT_PAY' AND expire_at<=#{now}") int expire(LocalDateTime now);
}
