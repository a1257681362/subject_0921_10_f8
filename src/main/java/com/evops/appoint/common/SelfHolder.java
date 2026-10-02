package com.evops.appoint.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 视图身份解析：请求头 X-Self-Code 传监考人员编码时为“本人视图”（只看自己的聘任与结算）；
 * 不传则为人事全量视图。鉴权仍走 Shiro HTTP Basic，这里只决定数据可见范围。
 */
public final class SelfHolder {

    private SelfHolder() {
    }

    /** 返回本人编码；为空表示人事全量视图。 */
    public static String currentSelfCode() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String code = request.getHeader(AppointConstants.SELF_HEADER);
            if (isBlank(code)) {
                code = request.getParameter("selfCode");
            }
            return isBlank(code) ? null : code.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
