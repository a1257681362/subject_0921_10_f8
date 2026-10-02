package com.evops.common;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 容器 / 安全层错误统一转成可读 JSON（如未认证 401、请求行非法 400、路径不存在 404），
 * 不向前端返回空白页或 Tomcat 默认 HTML。
 */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request, HttpServletResponse response) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusObj == null ? 500 : Integer.parseInt(statusObj.toString());

        String code;
        String message;
        switch (status) {
            case 401:
                code = "UNAUTHORIZED";
                message = "未认证或登录已失效，请使用 HTTP Basic（bootstrap/bootstrap）后重试";
                break;
            case 403:
                code = "FORBIDDEN";
                message = "已认证但无权访问该资源";
                break;
            case 404:
                code = "NOT_FOUND";
                message = "请求的接口不存在：" + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
                break;
            case 400:
                code = "BAD_REQUEST";
                message = "请求不合法（请检查中文等查询参数是否已做 URL 编码、请求行格式是否正确）";
                break;
            case 405:
                code = "METHOD_NOT_ALLOWED";
                message = "请求方法不被该接口支持";
                break;
            default:
                code = "INTERNAL_ERROR";
                message = "系统处理失败";
                break;
        }
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(ApiResponse.fail(code, message));
    }
}
