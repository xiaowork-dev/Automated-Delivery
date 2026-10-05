package com.xiaowork.autodelivery.security;
import com.xiaowork.autodelivery.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
@Component @RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserMapper users;
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String header=req.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) {
            var claims=jwt.verify(header.substring(7));
            if(claims!=null) {
                var user=users.selectById(claims.id());
                if(user!=null && Integer.valueOf(1).equals(user.getStatus()) && jwt.revision(user).equals(claims.passwordRevision())) {
                    var actor=new Actor(user.getId(),user.getUsername(),user.getRole());
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority("ROLE_"+actor.role()))));
                }
            }
        }
        chain.doFilter(req,res);
    }
}
