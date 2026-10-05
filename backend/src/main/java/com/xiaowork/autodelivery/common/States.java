package com.xiaowork.autodelivery.common;

public final class States {
    private States() {}
    public enum OrderStatus { WAIT_PAY, PAID, DELIVERED, COMPLETED, CANCELLED, EXPIRED }
    public enum CodeStatus { UNUSED, LOCKED, ASSIGNED, USED, EXPIRED, DISABLED }
    public enum ProductStatus { ON_SALE, OFF_SHELF }
    public enum UserRole { USER, ADMIN }
}
