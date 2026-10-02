package com.evops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;

@Configuration
public class ShiroConfig {

    /** 覆盖 Shiro 内置 authcBasic：未认证 / 账密错误返回可读 JSON 401。 */
    @Bean("authcBasic")
    public Filter authcBasicFilter(ObjectMapper objectMapper) {
        return new JsonBasicHttpAuthenticationFilter(objectMapper);
    }

    /** 覆盖 Shiro 内置 roles：已认证但角色不足返回可读 JSON 403（默认是 401，语义不符）。 */
    @Bean("roles")
    public Filter rolesFilter(ObjectMapper objectMapper) {
        return new org.apache.shiro.web.filter.authz.RolesAuthorizationFilter() {
            @Override
            protected boolean onAccessDenied(javax.servlet.ServletRequest request,
                                             javax.servlet.ServletResponse response) {
                javax.servlet.http.HttpServletResponse httpResponse =
                        (javax.servlet.http.HttpServletResponse) response;
                httpResponse.setStatus(403);
                httpResponse.setContentType("application/json;charset=UTF-8");
                try {
                    httpResponse.getWriter().write(objectMapper.writeValueAsString(
                            com.evops.common.ApiResponse.fail("FORBIDDEN",
                                    "已认证但无权访问该资源（当前账号仅可访问证书验真窗口）")));
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("写出 403 响应失败", e);
                }
                return false;
            }
        };
    }

    /**
     * 上述按 Shiro 名字（authcBasic/roles）定义的 Filter 只应在 Shiro 过滤器链内按名生效；
     * 禁用 Spring Boot 把它们再当普通 Servlet Filter 自动注册到 /*，否则 roles 会脱离链路拦截所有请求。
     */
    @Bean
    public FilterRegistrationBean<Filter> disableAuthcBasicServletRegistration(
            @org.springframework.beans.factory.annotation.Qualifier("authcBasic") Filter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> disableRolesServletRegistration(
            @org.springframework.beans.factory.annotation.Qualifier("roles") Filter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 顶替 Shiro 默认自动代理（JDK 动态代理，无法注入具体 Service 类）。
     * 配合排除 ShiroAnnotationProcessorAutoConfiguration，改用 CGLIB。
     */
    @Bean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    /** 排除 Shiro 注解处理器自动配置后，自行注册授权顾问，保留注解鉴权能力。 */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    @Bean
    public Realm localRealm() {
        SimpleAccountRealm realm = new SimpleAccountRealm();
        // 内部管理账号：全部接口
        realm.addAccount("bootstrap", "bootstrap", "system");
        // 用人单位账号：仅可访问对外验真窗口 /api/cert-verify（其他接口 roles[system] 返回 403）
        realm.addAccount("employer", "employer", "employer");
        return realm;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        chain.addPathDefinition("/api/health", "anon");
        chain.addPathDefinition("/error", "anon");
        // 对外验真窗口：任意已认证账号可访问（含用人单位 employer），走 HTTP Basic
        chain.addPathDefinition("/api/cert-verify", "authcBasic");
        // 其余 API 仅内部管理角色 system；用人单位访问返回可读 403
        chain.addPathDefinition("/api/**", "authcBasic,roles[system]");
        chain.addPathDefinition("/**", "authc");
        return chain;
    }
}
