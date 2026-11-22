package com.capitalone.loansvc.web;

import com.capitalone.loansvc.manager.CustomerManager;
import com.capitalone.loansvc.manager.LoanApplicationManager;
import jakarta.servlet.DispatcherType;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the legacy servlets and filter exactly as the removed WEB-INF/web.xml did, but with
 * Spring-injected collaborators instead of the static AppContext locator. Retained for this
 * phase (D-4 retirement deferred); see docs/design/technical-design-replatform.md (P3-1).
 */
@Configuration(proxyBeanMethods = false)
public class LegacyWebConfig {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> reg = new FilterRegistrationBean<>(new RequestIdFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        reg.setName("SecurityHeadersFilter");
        reg.addUrlPatterns("/*");
        reg.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return reg;
    }

    @Bean
    public ServletRegistrationBean<LoanApplyServlet> loanApplyServlet(LoanApplicationManager mgr) {
        return servlet("LoanApplyServlet", new LoanApplyServlet(mgr), "/apply");
    }

    @Bean
    public ServletRegistrationBean<EligibilityServlet> eligibilityServlet(LoanApplicationManager mgr) {
        return servlet("EligibilityServlet", new EligibilityServlet(mgr), "/eligibility");
    }

    @Bean
    public ServletRegistrationBean<UnderwriteServlet> underwriteServlet(LoanApplicationManager mgr) {
        return servlet("UnderwriteServlet", new UnderwriteServlet(mgr), "/underwrite");
    }

    @Bean
    public ServletRegistrationBean<CustomerServlet> customerServlet(CustomerManager customerManager) {
        return servlet("CustomerServlet", new CustomerServlet(customerManager), "/customer");
    }

    /**
     * web.xml declared index.jsp as the welcome file. Error pages keep Tomcat's standard report
     * (status, message, description) but hide the server version banner.
     */
    @Bean
    public TomcatContextCustomizer legacyTomcatContextCustomizer() {
        return context -> {
            context.addWelcomeFile("index.jsp");
            ErrorReportValve valve = new ErrorReportValve();
            valve.setShowReport(true);
            valve.setShowServerInfo(false);
            context.getParent().getPipeline().addValve(valve);
        };
    }

    private static <T extends jakarta.servlet.http.HttpServlet> ServletRegistrationBean<T> servlet(
            String name, T servlet, String mapping) {
        ServletRegistrationBean<T> reg = new ServletRegistrationBean<>(servlet, mapping);
        reg.setName(name);
        return reg;
    }
}
