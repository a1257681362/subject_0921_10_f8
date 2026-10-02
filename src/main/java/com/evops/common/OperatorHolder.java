package com.evops.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 经办人解析：优先取请求头 X-Operator，其次取 operator 参数，缺省 system。
 * 打印、发放等动作据此记录经办人。
 */
public final class OperatorHolder {

    private static final String DEFAULT_OPERATOR = "system";

    private OperatorHolder() {
    }

    public static String current() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return DEFAULT_OPERATOR;
            }
            HttpServletRequest request = attrs.getRequest();
            String operator = request.getHeader("X-Operator");
            if (isBlank(operator)) {
                operator = request.getParameter("operator");
            }
            return isBlank(operator) ? DEFAULT_OPERATOR : operator.trim();
        } catch (Exception e) {
            return DEFAULT_OPERATOR;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
