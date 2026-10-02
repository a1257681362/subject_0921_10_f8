package com.evops.common;

/**
 * 统一响应：失败必带可读状态码 code 与 message，不返回空白。
 */
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = "OK";
        response.message = "OK";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail("BIZ_ERROR", message);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = code == null ? "BIZ_ERROR" : code;
        response.message = message;
        return response;
    }

    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
