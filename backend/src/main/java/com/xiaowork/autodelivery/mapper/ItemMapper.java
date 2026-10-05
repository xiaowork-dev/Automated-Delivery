package com.xiaowork.autodelivery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaowork.autodelivery.model.Entities.OrderItem;
import org.apache.ibatis.annotations.Select;
public interface ItemMapper extends BaseMapper<OrderItem> {
    @Select("SELECT * FROM order_item WHERE order_id=#{orderId}") OrderItem byOrder(Long orderId);
}
