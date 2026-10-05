package com.xiaowork.autodelivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowork.autodelivery.common.BusinessException;
import com.xiaowork.autodelivery.common.States.ProductStatus;
import com.xiaowork.autodelivery.infra.RedisSupport;
import com.xiaowork.autodelivery.model.Dtos.ProductView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class RedisSupportTest {
    @Test void redisOutageFallsBackToDatabaseAndAllowsRequests() {
        var redis=mock(StringRedisTemplate.class);when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenThrow(new IllegalStateException("offline"));
        var support=new RedisSupport(true,redis,new ObjectMapper());var product=new ProductView(1L,"p","",BigDecimal.ONE,"","",ProductStatus.ON_SALE,1,"库存紧张");
        assertThat(support.product(1L,()->product)).isSameAs(product);assertThatCode(()->support.rateLimit("redeem","1",20)).doesNotThrowAnyException();
    }
    @Test void healthyRedisRateLimitReturnsExplicit429() {
        var redis=mock(StringRedisTemplate.class);when(redis.execute(any(RedisScript.class),anyList(),any(Object[].class))).thenReturn(21L);
        var support=new RedisSupport(true,redis,new ObjectMapper());assertThatThrownBy(()->support.rateLimit("redeem","1",20)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(429);
    }
}
