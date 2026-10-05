package com.xiaowork.autodelivery.common;

public class BusinessException extends RuntimeException {
    public final String code;
    public final int status;
    public BusinessException(String code, int status, String message) { super(message); this.code = code; this.status = status; }
    public static BusinessException missing(String what) { return new BusinessException("NOT_FOUND", 404, what + "不存在"); }
    public static BusinessException conflict(String code, String message) { return new BusinessException(code, 409, message); }
    public static BusinessException forbidden() { return new BusinessException("FORBIDDEN", 403, "无权访问此资源"); }
    public static BusinessException invalid(String message) { return new BusinessException("INVALID_ARGUMENT", 400, message); }
}
