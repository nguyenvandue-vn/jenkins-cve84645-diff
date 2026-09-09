package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.FederatedLoginService;
import hudson.security.captcha.CaptchaSupport;
import hudson.util.DescriptorList;
import io.jenkins.servlet.FilterConfigWrapper;
import io.jenkins.servlet.FilterWrapper;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.security.AcegiSecurityExceptionFilter;
import jenkins.security.AuthenticationSuccessHandler;
import jenkins.security.BasicHeaderProcessor;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.GrantedAuthorityImpl;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/* loaded from: SecurityRealm.class */
public abstract class SecurityRealm implements Describable<SecurityRealm>, ExtensionPoint {
    private CaptchaSupport captchaSupport;
    private transient SecurityComponents securityComponents;
    private static final ThreadLocal<Boolean> insideGetPostLogOutUrl = ThreadLocal.withInitial(() -> {
        return false;
    });
    public static final SecurityRealm NO_AUTHENTICATION = new None();

    @Deprecated
    public static final DescriptorList<SecurityRealm> LIST = new DescriptorList<>(SecurityRealm.class);
    private static final Logger LOGGER = Logger.getLogger(SecurityRealm.class.getName());
    public static final GrantedAuthority AUTHENTICATED_AUTHORITY2 = new SimpleGrantedAuthority("authenticated");

    @Deprecated
    public static final org.acegisecurity.GrantedAuthority AUTHENTICATED_AUTHORITY = new GrantedAuthorityImpl("authenticated");

    public abstract SecurityComponents createSecurityComponents();

    public IdStrategy getUserIdStrategy() {
        return IdStrategy.CASE_INSENSITIVE;
    }

    public IdStrategy getGroupIdStrategy() {
        return getUserIdStrategy();
    }

    @Deprecated
    public CliAuthenticator createCliAuthenticator(final CLICommand command) {
        throw new UnsupportedOperationException();
    }

    public Descriptor<SecurityRealm> getDescriptor() {
        return super.getDescriptor();
    }

    public String getAuthenticationGatewayUrl() {
        return "j_spring_security_check";
    }

    public String getLoginUrl() {
        return "login";
    }

    public boolean canLogOut() {
        return true;
    }

    protected String getPostLogOutUrl2(StaplerRequest2 req, Authentication auth) {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "getPostLogOutUrl2", new Class[]{StaplerRequest.class, Authentication.class})) {
            return getPostLogOutUrl2(StaplerRequest.fromStaplerRequest2(req), auth);
        }
        return getPostLogOutUrl2Impl(req, auth);
    }

    @Deprecated
    protected String getPostLogOutUrl2(StaplerRequest req, Authentication auth) {
        return getPostLogOutUrl2Impl(StaplerRequest.toStaplerRequest2(req), auth);
    }

    private String getPostLogOutUrl2Impl(StaplerRequest2 req, Authentication auth) {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "getPostLogOutUrl", new Class[]{StaplerRequest.class, org.acegisecurity.Authentication.class}) && !insideGetPostLogOutUrl.get().booleanValue()) {
            insideGetPostLogOutUrl.set(true);
            try {
                String postLogOutUrl = getPostLogOutUrl(StaplerRequest.fromStaplerRequest2(req), org.acegisecurity.Authentication.fromSpring(auth));
                insideGetPostLogOutUrl.set(false);
                return postLogOutUrl;
            } catch (Throwable th) {
                insideGetPostLogOutUrl.set(false);
                throw th;
            }
        }
        return req.getContextPath() + "/";
    }

    @Deprecated
    protected String getPostLogOutUrl(StaplerRequest req, org.acegisecurity.Authentication auth) {
        return getPostLogOutUrl2(StaplerRequest.toStaplerRequest2(req), auth.toSpring());
    }

    public CaptchaSupport getCaptchaSupport() {
        return this.captchaSupport;
    }

    public void setCaptchaSupport(CaptchaSupport captchaSupport) {
        this.captchaSupport = captchaSupport;
    }

    public List<Descriptor<CaptchaSupport>> getCaptchaSupportDescriptors() {
        return CaptchaSupport.all();
    }

    public void doLogout(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "doLogout", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                doLogout(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
                return;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
        doLogoutImpl(req, rsp);
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doLogout(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doLogoutImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    void doLogoutImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.clearContext();
        String contextPath = !req.getContextPath().isEmpty() ? req.getContextPath() : "/";
        resetRememberMeCookie(req, rsp, contextPath);
        clearStaleSessionCookies(req, rsp, contextPath);
        rsp.sendRedirect2(getPostLogOutUrl2(req, auth));
    }

    @SuppressFBWarnings(value = {"INSECURE_COOKIE"}, justification = "TODO needs triage")
    private void resetRememberMeCookie(StaplerRequest2 req, StaplerResponse2 rsp, String contextPath) {
        Cookie cookie = new Cookie("remember-me", "");
        cookie.setMaxAge(0);
        cookie.setSecure(req.isSecure());
        cookie.setHttpOnly(true);
        cookie.setPath(contextPath);
        rsp.addCookie(cookie);
    }

    private void clearStaleSessionCookies(StaplerRequest2 req, StaplerResponse2 rsp, String contextPath) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().startsWith("JSESSIONID.")) {
                    LOGGER.log(Level.FINE, "Removing cookie {0} during logout", cookie.getName());
                    cookie.setMaxAge(0);
                    cookie.setValue("");
                    rsp.addCookie(cookie);
                }
            }
        }
    }

    public boolean allowsSignup() {
        Class clz = getClass();
        return clz.getClassLoader().getResource(clz.getName().replace('.', '/') + "/signup.jelly") != null;
    }

    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "loadUserByUsername", new Class[]{String.class})) {
            try {
                return loadUserByUsername(username).toSpring();
            } catch (AcegiSecurityException x) {
                throw x.toSpring();
            } catch (DataAccessException x2) {
                throw x2.toSpring();
            }
        }
        return getSecurityComponents().userDetails2.loadUserByUsername(username);
    }

    @Deprecated
    public org.acegisecurity.userdetails.UserDetails loadUserByUsername(String username) throws org.acegisecurity.userdetails.UsernameNotFoundException, DataAccessException {
        try {
            return org.acegisecurity.userdetails.UserDetails.fromSpring(loadUserByUsername2(username));
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "loadGroupByGroupname", new Class[]{String.class})) {
            try {
                return loadGroupByGroupname(groupname);
            } catch (AcegiSecurityException x) {
                throw x.toSpring();
            } catch (DataAccessException x2) {
                throw x2.toSpring();
            }
        }
        if (Util.isOverridden(SecurityRealm.class, getClass(), "loadGroupByGroupname", new Class[]{String.class, Boolean.TYPE})) {
            try {
                return loadGroupByGroupname(groupname, fetchMembers);
            } catch (DataAccessException x3) {
                throw x3.toSpring();
            } catch (AcegiSecurityException x4) {
                throw x4.toSpring();
            }
        }
        throw new UserMayOrMayNotExistException2(groupname);
    }

    @Deprecated
    public GroupDetails loadGroupByGroupname(String groupname) throws org.acegisecurity.userdetails.UsernameNotFoundException, DataAccessException {
        try {
            return loadGroupByGroupname2(groupname, false);
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    @Deprecated
    public GroupDetails loadGroupByGroupname(String groupname, boolean fetchMembers) throws org.acegisecurity.userdetails.UsernameNotFoundException, DataAccessException {
        try {
            return loadGroupByGroupname2(groupname, fetchMembers);
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    public HttpResponse commenceSignup(FederatedLoginService.FederatedIdentity identity) {
        throw new UnsupportedOperationException();
    }

    public final void doCaptcha(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (this.captchaSupport != null) {
            String id = req.getSession().getId();
            rsp.setContentType("image/png");
            rsp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            rsp.setHeader("Pragma", "no-cache");
            rsp.setHeader("Expires", "0");
            this.captchaSupport.generateImage(id, rsp.getOutputStream());
        }
    }

    protected final boolean validateCaptcha(String text) {
        if (this.captchaSupport != null) {
            String id = Stapler.getCurrentRequest2().getSession().getId();
            return this.captchaSupport.validateCaptcha(id, text);
        }
        return true;
    }

    public synchronized SecurityComponents getSecurityComponents() {
        if (this.securityComponents == null) {
            this.securityComponents = createSecurityComponents();
        }
        return this.securityComponents;
    }

    public Filter createFilter(FilterConfig filterConfig) {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "createFilter", new Class[]{javax.servlet.FilterConfig.class})) {
            return FilterWrapper.toJakartaFilter(createFilter(filterConfig != null ? FilterConfigWrapper.fromJakartaFilterConfig(filterConfig) : null));
        }
        return createFilterImpl(filterConfig);
    }

    @Deprecated
    public javax.servlet.Filter createFilter(javax.servlet.FilterConfig filterConfig) {
        return FilterWrapper.fromJakartaFilter(createFilterImpl(filterConfig != null ? FilterConfigWrapper.toJakartaFilterConfig(filterConfig) : null));
    }

    private Filter createFilterImpl(FilterConfig filterConfig) {
        LOGGER.entering(SecurityRealm.class.getName(), "createFilterImpl");
        SecurityComponents sc = getSecurityComponents();
        List<Filter> filters = new ArrayList<>();
        HttpSessionSecurityContextRepository httpSessionSecurityContextRepository = new HttpSessionSecurityContextRepository();
        httpSessionSecurityContextRepository.setAllowSessionCreation(false);
        filters.add(new HttpSessionContextIntegrationFilter2(httpSessionSecurityContextRepository));
        BasicHeaderProcessor bhp = new BasicHeaderProcessor();
        BasicAuthenticationEntryPoint basicAuthenticationEntryPoint = new BasicAuthenticationEntryPoint();
        basicAuthenticationEntryPoint.setRealmName("Jenkins");
        bhp.setAuthenticationEntryPoint(basicAuthenticationEntryPoint);
        bhp.setRememberMeServices(sc.rememberMe2);
        filters.add(bhp);
        AuthenticationProcessingFilter2 apf = new AuthenticationProcessingFilter2(getAuthenticationGatewayUrl());
        apf.setAuthenticationManager(sc.manager2);
        if (SystemProperties.getInteger(SecurityRealm.class.getName() + ".sessionFixationProtectionMode", 1).intValue() == 1) {
            apf.setSessionAuthenticationStrategy(new SessionFixationProtectionStrategy());
        }
        apf.setRememberMeServices(sc.rememberMe2);
        AuthenticationSuccessHandler successHandler = new AuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("from");
        apf.setAuthenticationSuccessHandler(successHandler);
        apf.setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler("/loginError"));
        filters.add(apf);
        filters.add(new RememberMeAuthenticationFilter(sc.manager2, sc.rememberMe2));
        filters.addAll(commonFilters());
        return new ChainedServletFilter2(filters);
    }

    protected final List<Filter> commonFilters() {
        Filter anonymousAuthenticationFilter = new AnonymousAuthenticationFilter("anonymous", "anonymous", List.of(new SimpleGrantedAuthority("anonymous")));
        Filter exceptionTranslationFilter = new ExceptionTranslationFilter(new HudsonAuthenticationEntryPoint("/" + getLoginUrl() + "?from={0}"));
        exceptionTranslationFilter.setAccessDeniedHandler(new AccessDeniedHandlerImpl());
        return Arrays.asList(anonymousAuthenticationFilter, exceptionTranslationFilter, new UnwrapSecurityExceptionFilter(), new AcegiSecurityExceptionFilter());
    }

    @Restricted({DoNotUse.class})
    public static String getFrom() {
        HttpSession session;
        Object attribute;
        String from = null;
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request != null) {
            from = request.getParameter("from");
        }
        if (request != null && request.getRequestURI().equals(request.getContextPath() + "/404") && (session = request.getSession(false)) != null && (attribute = session.getAttribute("from")) != null) {
            from = attribute.toString();
        }
        if (from == null && request != null && request.getRequestURI() != null && !request.getRequestURI().equals(request.getContextPath() + "/loginError") && !request.getRequestURI().equals(request.getContextPath() + "/login") && !request.getRequestURI().equals(request.getContextPath() + "/404")) {
            from = request.getRequestURI();
        }
        if (from == null || from.isBlank()) {
            from = "/";
        }
        String returnValue = URLEncoder.encode(from.trim(), StandardCharsets.UTF_8);
        return (returnValue == null || returnValue.isBlank()) ? "/" : returnValue;
    }

    /* loaded from: SecurityRealm$None.class */
    private static class None extends SecurityRealm {
        private None() {
        }

        @Override // hudson.security.SecurityRealm
        public SecurityComponents createSecurityComponents() {
            return new SecurityComponents(authentication -> {
                return authentication;
            }, username -> {
                throw new UsernameNotFoundException(username);
            });
        }

        @Override // hudson.security.SecurityRealm
        public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            throw new UsernameNotFoundException(groupname);
        }

        @Override // hudson.security.SecurityRealm
        public Filter createFilter(FilterConfig filterConfig) {
            return new ChainedServletFilter2();
        }

        private Object readResolve() {
            return NO_AUTHENTICATION;
        }

        @Extension(ordinal = -100.0d)
        @Symbol({"none"})
        /* loaded from: SecurityRealm$None$DescriptorImpl.class */
        public static class DescriptorImpl extends Descriptor<SecurityRealm> {
            @NonNull
            public String getDisplayName() {
                return Messages.NoneSecurityRealm_DisplayName();
            }

            /* renamed from: newInstance, reason: merged with bridge method [inline-methods] */
            public SecurityRealm m71newInstance(StaplerRequest2 req, JSONObject formData) throws Descriptor.FormException {
                return SecurityRealm.NO_AUTHENTICATION;
            }
        }
    }

    /* loaded from: SecurityRealm$SecurityComponents.class */
    public static final class SecurityComponents {
        public final AuthenticationManager manager2;

        @Deprecated
        public final org.acegisecurity.AuthenticationManager manager;
        public final UserDetailsService userDetails2;

        @Deprecated
        public final org.acegisecurity.userdetails.UserDetailsService userDetails;
        public final RememberMeServices rememberMe2;

        @Deprecated
        public final org.acegisecurity.ui.rememberme.RememberMeServices rememberMe;
        static final /* synthetic */ boolean $assertionsDisabled;

        static {
            $assertionsDisabled = !SecurityRealm.class.desiredAssertionStatus();
        }

        public SecurityComponents() {
            this((AuthenticationManager) new AuthenticationManagerProxy());
        }

        public SecurityComponents(AuthenticationManager manager) {
            this(manager, (UserDetailsService) new UserDetailsServiceProxy());
        }

        @Deprecated
        public SecurityComponents(org.acegisecurity.AuthenticationManager manager) {
            this(manager.toSpring());
        }

        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails) {
            this(manager, userDetails, createRememberMeService(userDetails));
        }

        @Deprecated
        public SecurityComponents(org.acegisecurity.AuthenticationManager manager, org.acegisecurity.userdetails.UserDetailsService userDetails) {
            this(manager.toSpring(), userDetails.toSpring());
        }

        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails, RememberMeServices rememberMe) {
            if (!$assertionsDisabled && (manager == null || userDetails == null || rememberMe == null)) {
                throw new AssertionError();
            }
            this.manager2 = manager;
            this.userDetails2 = userDetails;
            this.rememberMe2 = rememberMe;
            this.manager = org.acegisecurity.AuthenticationManager.fromSpring(manager);
            this.userDetails = org.acegisecurity.userdetails.UserDetailsService.fromSpring(userDetails);
            this.rememberMe = org.acegisecurity.ui.rememberme.RememberMeServices.fromSpring(rememberMe);
        }

        @Deprecated
        public SecurityComponents(org.acegisecurity.AuthenticationManager manager, org.acegisecurity.userdetails.UserDetailsService userDetails, org.acegisecurity.ui.rememberme.RememberMeServices rememberMe) {
            this(manager.toSpring(), userDetails.toSpring(), rememberMe.toSpring());
        }

        private static RememberMeServices createRememberMeService(UserDetailsService uds) {
            TokenBasedRememberMeServices2 rms = new TokenBasedRememberMeServices2(uds);
            rms.setParameter("remember_me");
            return rms;
        }
    }

    public static DescriptorExtensionList<SecurityRealm, Descriptor<SecurityRealm>> all() {
        return Jenkins.get().getDescriptorList(SecurityRealm.class);
    }
}
