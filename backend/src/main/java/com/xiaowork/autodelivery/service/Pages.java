package com.xiaowork.autodelivery.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaowork.autodelivery.common.Api;
import com.xiaowork.autodelivery.common.BusinessException;
import java.util.function.Function;
public final class Pages {
    private Pages() {}
    public static <E,V> Api.Page<V> of(BaseMapper<E> mapper, QueryWrapper<E> query,int page,int size,Function<E,V> convert) {
        if(page<1 || page>1000000 || size<1 || size>100) throw BusinessException.invalid("分页参数超出范围");
        long total=mapper.selectCount(query);
        query.orderByDesc("id").last("LIMIT "+size+" OFFSET "+((long)(page-1)*size));
        return new Api.Page<>(mapper.selectList(query).stream().map(convert).toList(),total,page,size);
    }
    public static boolean text(String value) { return value!=null && !value.isBlank(); }
}
