package com.xiaowork.autodelivery.model;

import com.xiaowork.autodelivery.common.States.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {}
    public record Credentials(@NotBlank @Pattern(regexp="[a-zA-Z0-9_\\-]{3,50}") String username,
                              @NotBlank @Size(min=8,max=72) String password) {}
    public record PasswordChange(@NotBlank @Size(max=72) String currentPassword, @NotBlank @Size(min=8,max=72) String newPassword) {}
    public record UserView(Long id, String username, UserRole role, Integer status, LocalDateTime createdAt) {}
    public record AuthView(String token, UserView user) {}
    public record ProductInput(@NotBlank @Size(max=120) String name, @Size(max=200) String subtitle,
                               @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal price,
                               @Size(max=500) String coverUrl, @Size(max=20000) String description, ProductStatus status) {}
    public record ProductView(Long id, String name, String subtitle, BigDecimal price, String coverUrl, String description,
                              ProductStatus status, long stock, String stockLabel) {}
    public record NewOrder(@NotNull @Positive Long productId) {}
    public record CodeView(Long id, String code, CodeStatus status, LocalDateTime expiredAt, LocalDateTime usedAt) {}
    public record OrderView(Long id, String orderNo, Long userId, Long productId, String productName, BigDecimal unitPrice,
                            BigDecimal amount, OrderStatus status, LocalDateTime createdAt, LocalDateTime expireAt,
                            LocalDateTime paidAt, LocalDateTime deliveredAt, CodeView redeemCode) {}
    public record ImportCodes(@NotNull @Positive Long productId, @NotBlank @Size(max=1500000) String codes, @Future LocalDateTime expiredAt) {}
    public record ImportResult(int successCount, int duplicateCount, int invalidCount) {}
    public record DisableCode(@NotBlank @Size(max=200) String reason) {}
    public record UserStatus(@NotNull @Min(0) @Max(1) Integer status) {}
    public record RedeemInput(@NotBlank @Size(max=128) String code) {}
    public record RedeemView(String orderNo, String productName, OrderStatus status, LocalDateTime usedAt, String message) {}
    public record RedeemRecordView(Long id, String orderNo, String productName, String result, LocalDateTime createdAt) {}
    public record AdminCodeView(Long id, Long productId, String productName, String code, CodeStatus status, Long orderId,
                                String orderNo, Long assignedUserId, LocalDateTime assignedAt, LocalDateTime usedAt,
                                LocalDateTime expiredAt, LocalDateTime createdAt) {}
    public record Dashboard(long productCount, long todayOrders, long availableCodes, long deliveredOrders, List<ProductView> lowStockProducts) {}
}
