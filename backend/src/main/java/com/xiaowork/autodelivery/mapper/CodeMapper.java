package com.xiaowork.autodelivery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaowork.autodelivery.model.Entities.Code;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
public interface CodeMapper extends BaseMapper<Code> {
    @Select("SELECT COUNT(*) FROM redeem_code WHERE product_id=#{productId} AND status='UNUSED' AND (expired_at IS NULL OR expired_at>#{now})")
    long stock(@Param("productId") Long productId, @Param("now") LocalDateTime now);
    @Select("SELECT * FROM redeem_code WHERE product_id=#{productId} AND status='UNUSED' AND (expired_at IS NULL OR expired_at>#{now}) ORDER BY id LIMIT 1 FOR UPDATE")
    Code lockAvailable(@Param("productId") Long productId, @Param("now") LocalDateTime now);
    @Select("SELECT * FROM redeem_code WHERE order_id=#{orderId}") Code byOrder(Long orderId);
    @Select("SELECT * FROM redeem_code WHERE code=#{code}") Code byCode(String code);
    @Select("SELECT * FROM redeem_code WHERE id=#{id} FOR UPDATE") Code lockById(Long id);
    @Update("UPDATE redeem_code SET status='ASSIGNED', order_id=#{orderId}, assigned_user_id=#{userId}, assigned_at=#{now} WHERE id=#{codeId} AND status='UNUSED' AND (expired_at IS NULL OR expired_at>#{now})")
    int assign(@Param("codeId") Long codeId, @Param("orderId") Long orderId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
    @Update("UPDATE redeem_code SET status='USED',used_by=#{userId},used_at=#{now} WHERE id=#{codeId} AND status='ASSIGNED' AND assigned_user_id=#{userId} AND (expired_at IS NULL OR expired_at>#{now})")
    int use(@Param("codeId") Long codeId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
    @Update("UPDATE redeem_code SET status='EXPIRED' WHERE status IN ('UNUSED','ASSIGNED') AND expired_at<=#{now}") int expire(LocalDateTime now);
}
