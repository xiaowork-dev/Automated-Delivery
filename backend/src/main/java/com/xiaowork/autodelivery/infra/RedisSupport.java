package com.xiaowork.autodelivery.infra;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowork.autodelivery.common.BusinessException;
import com.xiaowork.autodelivery.model.Dtos.ProductView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
@Component
public class RedisSupport {
    private final boolean enabled;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private static final DefaultRedisScript<Long> RATE=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",Long.class);
    public RedisSupport(@Value("${app.redis-enabled:false}") boolean enabled,StringRedisTemplate redis,ObjectMapper json) { this.enabled=enabled;this.redis=redis;this.json=json; }
    public ProductView product(Long id,Supplier<ProductView> loader) {
        if(!enabled) return loader.get();
        String key="delivery:product:"+id;
        try { String cached=redis.opsForValue().get(key);if(cached!=null) return json.readValue(cached,ProductView.class); } catch(Exception ignored) { /* DB is always authoritative */ }
        ProductView value=loader.get();
        try { redis.opsForValue().set(key,json.writeValueAsString(value),Duration.ofSeconds(30)); } catch(Exception ignored) { }
        return value;
    }
    public void evictProduct(Long id) {
        if(!enabled)return;
        Runnable eviction=()->{try { redis.delete("delivery:product:"+id); } catch(Exception ignored) { }};
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){eviction.run();}});
        } else eviction.run();
    }
    public void rateLimit(String purpose,String identity,int limit) {
        if(!enabled) return;
        Long n;
        try { n=redis.execute(RATE,List.of("delivery:rate:"+purpose+":"+Integer.toHexString(identity.hashCode())),"60"); } catch(Exception ignored) { return; }
        if(n!=null && n>limit) throw new BusinessException("TOO_MANY_REQUESTS",429,"操作过于频繁，请稍后重试");
    }
}
