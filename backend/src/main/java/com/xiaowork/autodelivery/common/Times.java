package com.xiaowork.autodelivery.common;
import java.time.LocalDateTime;
import java.time.ZoneId;
public final class Times {
    private Times() {}
    public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
}
