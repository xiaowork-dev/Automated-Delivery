package com.xiaowork.autodelivery.service;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xiaowork.autodelivery.common.BusinessException;
import com.xiaowork.autodelivery.common.States.UserRole;
import com.xiaowork.autodelivery.common.Times;
import com.xiaowork.autodelivery.mapper.UserMapper;
import com.xiaowork.autodelivery.model.Dtos.*;
import com.xiaowork.autodelivery.model.Entities.User;
import com.xiaowork.autodelivery.security.Actor;
import com.xiaowork.autodelivery.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
@Service @RequiredArgsConstructor
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    public static UserView view(User u) { return new UserView(u.getId(),u.getUsername(),u.getRole(),u.getStatus(),u.getCreatedAt()); }
    public static void validatePassword(String password) { if(password.getBytes(StandardCharsets.UTF_8).length>72) throw BusinessException.invalid("密码 UTF-8 编码不能超过72字节"); }
    @Transactional public AuthView register(Credentials input) {
        validatePassword(input.password());
        if(users.byUsername(input.username())!=null) throw BusinessException.conflict("USERNAME_EXISTS","用户名已存在");
        var u=new User();u.setUsername(input.username());u.setPasswordHash(passwords.encode(input.password()));u.setRole(UserRole.USER);u.setStatus(1);u.setCreatedAt(Times.now());u.setUpdatedAt(Times.now());users.insert(u);
        return new AuthView(jwt.issue(u),view(u));
    }
    public AuthView login(Credentials input) {
        validatePassword(input.password());
        var u=users.byUsername(input.username());
        if(u==null || !passwords.matches(input.password(),u.getPasswordHash())) throw new BusinessException("INVALID_CREDENTIALS",401,"用户名或密码错误");
        if(u.getStatus()!=1) throw new BusinessException("ACCOUNT_DISABLED",403,"账号已停用");
        return new AuthView(jwt.issue(u),view(u));
    }
    public UserView me() { return view(users.selectById(Actor.current().id())); }
    @Transactional public Void changePassword(PasswordChange input) {
        validatePassword(input.currentPassword());
        validatePassword(input.newPassword());
        var u=users.selectById(Actor.current().id());
        if(!passwords.matches(input.currentPassword(),u.getPasswordHash())) throw new BusinessException("INVALID_CREDENTIALS",401,"当前密码错误");
        // Compare the original hash so concurrent password changes cannot overwrite each other.
        int updated=users.update(null,new UpdateWrapper<User>().eq("id",u.getId()).eq("password_hash",u.getPasswordHash()).set("password_hash",passwords.encode(input.newPassword())).set("updated_at",Times.now()));
        if(updated!=1) throw BusinessException.conflict("DATA_CONFLICT","密码已修改，请重新登录");
        return null;
    }
}
