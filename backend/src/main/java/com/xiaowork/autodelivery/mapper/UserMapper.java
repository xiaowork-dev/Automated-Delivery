package com.xiaowork.autodelivery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaowork.autodelivery.model.Entities.User;
import org.apache.ibatis.annotations.Select;
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM sys_user WHERE username=#{username}") User byUsername(String username);
}
