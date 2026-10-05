package com.xiaowork.autodelivery.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaowork.autodelivery.common.States.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Entities {
    private Entities() {}
    @Data @TableName("sys_user") public static class User {
        @TableId(type = IdType.AUTO) private Long id;
        private String username, passwordHash;
        private UserRole role;
        private Integer status;
        private LocalDateTime createdAt, updatedAt;
    }
    @Data @TableName("product") public static class Product {
        @TableId(type = IdType.AUTO) private Long id;
        private String name, subtitle, coverUrl, description;
        private BigDecimal price;
        private ProductStatus status;
        private LocalDateTime createdAt, updatedAt;
    }
    @Data @TableName("order_info") public static class Order {
        @TableId(type = IdType.AUTO) private Long id;
        private String orderNo, payType;
        private Long userId;
        private BigDecimal totalAmount;
        private OrderStatus status;
        private Integer version;
        private LocalDateTime paidAt, deliveredAt, expireAt, createdAt, updatedAt;
    }
    @Data @TableName("order_item") public static class OrderItem {
        @TableId(type = IdType.AUTO) private Long id;
        private Long orderId, productId;
        private String productName;
        private BigDecimal unitPrice, subtotal;
        private Integer quantity;
    }
    @Data @TableName("redeem_code") public static class Code {
        @TableId(type = IdType.AUTO) private Long id;
        private Long productId, orderId, assignedUserId, usedBy;
        private String code;
        private CodeStatus status;
        private LocalDateTime assignedAt, usedAt, expiredAt, createdAt;
    }
    @Data @TableName("redeem_record") public static class RedeemRecord {
        @TableId(type = IdType.AUTO) private Long id;
        private Long redeemCodeId, orderId, userId;
        private String result, reason, requestId, ip;
        private LocalDateTime createdAt;
    }
    @Data @TableName("operation_log") public static class OperationLog {
        @TableId(type = IdType.AUTO) private Long id;
        private Long operatorId, targetId;
        private String action, targetType, detail;
        private LocalDateTime createdAt;
    }
    @Data @TableName("delivery_log") public static class DeliveryLog {
        @TableId(type = IdType.AUTO) private Long id;
        private Long orderId, codeId, userId, productId;
        private String requestId, result;
        private LocalDateTime createdAt;
    }
}
