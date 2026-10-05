package com.xiaowork.autodelivery.security;
import com.xiaowork.autodelivery.common.BusinessException;
import com.xiaowork.autodelivery.common.States.UserRole;
import org.springframework.security.core.context.SecurityContextHolder;
public record Actor(Long id, String username, UserRole role) {
    public boolean admin() { return role == UserRole.ADMIN; }
    public static Actor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Actor actor)) throw new BusinessException("AUTH_REQUIRED",401,"请先登录");
        return actor;
    }
    public void requireOwner(Long owner) { if (!id.equals(owner) && !admin()) throw BusinessException.forbidden(); }
}
