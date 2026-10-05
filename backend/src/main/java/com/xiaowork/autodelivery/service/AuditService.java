package com.xiaowork.autodelivery.service;
import com.xiaowork.autodelivery.common.Times;
import com.xiaowork.autodelivery.mapper.LogMapper;
import com.xiaowork.autodelivery.model.Entities.OperationLog;
import com.xiaowork.autodelivery.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AuditService {
    private final LogMapper logs;
    public void record(String action,String targetType,Long id,String detail) {
        var log=new OperationLog();log.setOperatorId(Actor.current().id());log.setAction(action);log.setTargetType(targetType);log.setTargetId(id);log.setDetail(detail);log.setCreatedAt(Times.now());logs.insert(log);
    }
}
