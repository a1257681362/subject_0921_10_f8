package com.evops.config;

import com.evops.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 未认证时返回可读 JSON 401，而不是空白响应。
 */
public class JsonBasicHttpAuthenticationFilter extends BasicHttpAuthenticationFilter {

    private final ObjectMapper objectMapper;

    public JsonBasicHttpAuthenticationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        setApplicationName("evops");
    }

    @Override
    protected boolean sendChallenge(ServletRequest request, ServletResponse response) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 保留标准 WWW-Authenticate，便于 HTTP 客户端弹出/处理 Basic
        httpResponse.setHeader(AUTHENTICATE_HEADER, "Basic realm=\"" + getApplicationName() + "\"");
        httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.fail("UNAUTHORIZED",
                "未认证或登录已失效，请使用 HTTP Basic（bootstrap/bootstrap）后重试：" + httpRequest.getRequestURI());
        try {
            httpResponse.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException("写出 401 响应失败", e);
        }
        return false;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        // 已带凭据但校验失败（账密错误）也走同样的可读 401
        if (isLoginAttempt(request, response)) {
            return executeLogin(request, response);
        }
        return sendChallenge(request, response);
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e,
                                     ServletRequest request, ServletResponse response) {
        return sendChallenge(request, response);
    }
}
