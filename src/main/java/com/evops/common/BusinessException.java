package com.evops.common;

/**
 * 业务异常：接口失败时返回可读状态，由 GlobalExceptionHandler 转成 ApiResponse.fail。
 */
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String message) {
        this("BIZ_ERROR", message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
