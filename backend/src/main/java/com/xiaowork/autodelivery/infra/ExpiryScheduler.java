package com.xiaowork.autodelivery.infra;
import com.xiaowork.autodelivery.common.Times;
import com.xiaowork.autodelivery.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class ExpiryScheduler {
    private final OrderMapper orders;
    private final CodeMapper codes;
    @Scheduled(fixedDelayString="${app.expiry-scan-ms:60000}",initialDelayString="${app.expiry-initial-delay-ms:10000}")
    @Transactional public void expire() {
        var now=Times.now();int orderCount=orders.expire(now);int codeCount=codes.expire(now);
        if(orderCount+codeCount>0)log.info("expiry orders={} codes={}",orderCount,codeCount);
    }
}
