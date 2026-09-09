package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.model.Messages;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.security.FederatedLoginService;
import hudson.security.captcha.CaptchaSupport;
import hudson.util.FormValidation;
import hudson.util.PluginServletFilter;
import hudson.util.Protector;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import jenkins.model.Jenkins;
import jenkins.security.FIPS140;
import jenkins.security.NonePasswordComplexityRule;
import jenkins.security.PasswordComplexityException;
import jenkins.security.PasswordComplexityRule;
import jenkins.security.SecurityListener;
import jenkins.security.seed.UserSeedProperty;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CompatibleFilter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/* loaded from: HudsonPrivateSecurityRealm.class */
public class HudsonPrivateSecurityRealm extends AbstractPasswordBasedSecurityRealm implements ModelObject, AccessControlled {
    private static final int FIPS_PASSWORD_LENGTH = 14;
    private static final String DEFAULT_ID_REGEX = "^[\\w-]+$";
    private final boolean disableSignup;
    private final boolean enableCaptcha;
    private PasswordComplexityRule passwordComplexityRule;
    static final PasswordHashEncoder PASSWORD_HASH_ENCODER;
    private static final String PBKDF2 = "$PBKDF2";
    private static final String JBCRYPT = "#jbcrypt:";
    public static final MultiPasswordEncoder PASSWORD_ENCODER;
    private static final String ENCODED_INVALID_USER_PASSWORD;
    private static final Filter CREATE_FIRST_USER_FILTER;
    private static final Logger LOGGER;
    private static String ID_REGEX = System.getProperty(HudsonPrivateSecurityRealm.class.getName() + ".ID_REGEX");
    private static final String FEDERATED_IDENTITY_SESSION_KEY = HudsonPrivateSecurityRealm.class.getName() + ".federatedIdentity";
    private static final Collection<? extends GrantedAuthority> TEST_AUTHORITY = Set.of(AUTHENTICATED_AUTHORITY2);

    static {
        PASSWORD_HASH_ENCODER = FIPS140.useCompliantAlgorithms() ? new PBKDF2PasswordEncoder() : new JBCryptEncoder();
        PASSWORD_ENCODER = new MultiPasswordEncoder();
        ENCODED_INVALID_USER_PASSWORD = PASSWORD_ENCODER.encode(generatePassword());
        CREATE_FIRST_USER_FILTER = new CompatibleFilter() { // from class: hudson.security.HudsonPrivateSecurityRealm.2
            public void init(FilterConfig config) throws ServletException {
            }

            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                if (req.getRequestURI().equals(req.getContextPath() + "/") || req.getRequestURI().equals(req.getContextPath() + "/manage")) {
                    if (needsToCreateFirstUser()) {
                        ((HttpServletResponse) response).sendRedirect("securityRealm/firstUser");
                        return;
                    } else {
                        PluginServletFilter.removeFilter(this);
                        chain.doFilter(request, response);
                        return;
                    }
                }
                chain.doFilter(request, response);
            }

            private boolean needsToCreateFirstUser() {
                return !HudsonPrivateSecurityRealm.hasSomeUser() && (Jenkins.get().getSecurityRealm() instanceof HudsonPrivateSecurityRealm);
            }

            public void destroy() {
            }
        };
        LOGGER = Logger.getLogger(HudsonPrivateSecurityRealm.class.getName());
    }

    @Deprecated
    public HudsonPrivateSecurityRealm(boolean allowsSignup) {
        this(allowsSignup, false, (CaptchaSupport) null);
    }

    @DataBoundConstructor
    public HudsonPrivateSecurityRealm(boolean allowsSignup, boolean enableCaptcha, CaptchaSupport captchaSupport) {
        this.passwordComplexityRule = new NonePasswordComplexityRule();
        this.disableSignup = !allowsSignup;
        this.enableCaptcha = enableCaptcha;
        setCaptchaSupport(captchaSupport);
        if (!allowsSignup && !hasSomeUser()) {
            try {
                PluginServletFilter.addFilter(CREATE_FIRST_USER_FILTER);
            } catch (ServletException e) {
                throw new AssertionError(e);
            }
        }
    }

    public boolean allowsSignup() {
        return !this.disableSignup;
    }

    @Restricted({NoExternalUse.class})
    public boolean getAllowsSignup() {
        return allowsSignup();
    }

    public boolean isEnableCaptcha() {
        return this.enableCaptcha;
    }

    @Restricted({NoExternalUse.class})
    public PasswordComplexityRule getPasswordComplexityRule() {
        return this.passwordComplexityRule;
    }

    @DataBoundSetter
    public void setPasswordComplexityRule(PasswordComplexityRule passwordComplexityRule) {
        this.passwordComplexityRule = passwordComplexityRule != null ? passwordComplexityRule : new NonePasswordComplexityRule();
    }

    private Object readResolve() {
        if (this.passwordComplexityRule == null) {
            this.passwordComplexityRule = new NonePasswordComplexityRule();
        }
        return this;
    }

    private static boolean hasSomeUser() {
        for (User u : User.getAll()) {
            if (u.getProperty(Details.class) != null) {
                return true;
            }
        }
        return false;
    }

    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
        throw new UsernameNotFoundException(groupname);
    }

    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        return load(username).asUserDetails();
    }

    @Restricted({NoExternalUse.class})
    public Details load(String username) throws UsernameNotFoundException {
        User u = User.getById(username, false);
        Details p = u != null ? (Details) u.getProperty(Details.class) : null;
        if (p == null) {
            throw new UsernameNotFoundException("Password is not set: " + username);
        }
        if (p.getUser() == null) {
            throw new AssertionError();
        }
        return p;
    }

    protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
        try {
            Details u = load(username);
            if (!u.isPasswordCorrect(password)) {
                throw new BadCredentialsException("Bad credentials");
            }
            return u.asUserDetails();
        } catch (UsernameNotFoundException ex) {
            PASSWORD_ENCODER.matches(password, ENCODED_INVALID_USER_PASSWORD);
            throw ex;
        }
    }

    public HttpResponse commenceSignup(final FederatedLoginService.FederatedIdentity identity) {
        Stapler.getCurrentRequest2().getSession().setAttribute(FEDERATED_IDENTITY_SESSION_KEY, identity);
        return new ForwardToView(this, "signupWithFederatedIdentity.jelly") { // from class: hudson.security.HudsonPrivateSecurityRealm.1
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                SignupInfo si = new SignupInfo(identity);
                si.errorMessage = Messages.HudsonPrivateSecurityRealm_WouldYouLikeToSignUp(identity.getPronoun(), identity.getIdentifier());
                req.setAttribute("data", si);
                super.generateResponse(req, rsp, node);
            }
        };
    }

    @RequirePOST
    public User doCreateAccountWithFederatedIdentity(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        User u = _doCreateAccount(req, rsp, "signupWithFederatedIdentity.jelly");
        if (u != null) {
            ((FederatedLoginService.FederatedIdentity) req.getSession().getAttribute(FEDERATED_IDENTITY_SESSION_KEY)).addTo(u);
        }
        return u;
    }

    @RequirePOST
    public User doCreateAccount(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        return _doCreateAccount(req, rsp, "signup.jelly");
    }

    private User _doCreateAccount(StaplerRequest2 req, StaplerResponse2 rsp, String formView) throws ServletException, IOException {
        if (!allowsSignup()) {
            throw HttpResponses.errorWithoutStack(401, "User sign up is prohibited");
        }
        boolean firstUser = !hasSomeUser();
        User u = createAccount(req, rsp, this.enableCaptcha, formView);
        if (u != null) {
            if (firstUser) {
                tryToMakeAdmin(u);
            }
            loginAndTakeBack(req, rsp, u);
        }
        return u;
    }

    private void loginAndTakeBack(StaplerRequest2 req, StaplerResponse2 rsp, User u) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        req.getSession(true);
        Authentication a = getSecurityComponents().manager2.authenticate(new UsernamePasswordAuthenticationToken(u.getId(), req.getParameter("password1")));
        SecurityContextHolder.getContext().setAuthentication(a);
        SecurityListener.fireLoggedIn(u.getId());
        req.getView(this, "success.jelly").forward(req, rsp);
    }

    @RequirePOST
    public void doCreateAccountByAdmin(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        createAccountByAdmin(req, rsp, "addUserDialog.jelly", ".");
    }

    @Restricted({NoExternalUse.class})
    public User createAccountByAdmin(StaplerRequest2 req, StaplerResponse2 rsp, String addUserView, String successView) throws IOException, ServletException {
        checkPermission(Jenkins.ADMINISTER);
        User u = createAccount(req, rsp, false, addUserView);
        if (u != null && successView != null) {
            rsp.sendRedirect(successView);
        }
        return u;
    }

    @Restricted({NoExternalUse.class})
    public User createAccountFromSetupWizard(StaplerRequest2 req) throws IOException, AccountCreationFailedException {
        checkPermission(Jenkins.ADMINISTER);
        SignupInfo si = validateAccountCreationForm(req, false);
        if (!si.errors.isEmpty()) {
            String messages = getErrorMessages(si);
            throw new AccountCreationFailedException(messages);
        }
        return createAccount(si);
    }

    private String getErrorMessages(SignupInfo si) {
        StringBuilder messages = new StringBuilder();
        for (String message : si.errors.values()) {
            messages.append(message).append(" | ");
        }
        return messages.toString();
    }

    @RequirePOST
    public synchronized void doCreateFirstAccount(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (hasSomeUser()) {
            rsp.sendError(401, "First user was already created");
            return;
        }
        User u = createAccount(req, rsp, false, "firstUser.jelly");
        if (u != null) {
            tryToMakeAdmin(u);
            loginAndTakeBack(req, rsp, u);
        }
    }

    private void tryToMakeAdmin(User u) {
        AuthorizationStrategy as = Jenkins.get().getAuthorizationStrategy();
        Iterator it = ExtensionList.lookup(PermissionAdder.class).iterator();
        while (it.hasNext()) {
            PermissionAdder adder = (PermissionAdder) it.next();
            if (adder.add(as, u, Jenkins.ADMINISTER)) {
                return;
            }
        }
    }

    private User createAccount(StaplerRequest2 req, StaplerResponse2 rsp, boolean validateCaptcha, String formView) throws ServletException, IOException {
        SignupInfo si = validateAccountCreationForm(req, validateCaptcha);
        if (!si.errors.isEmpty()) {
            req.getView(this, formView).forward(req, rsp);
            return null;
        }
        return createAccount(si);
    }

    @SuppressFBWarnings(value = {"UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"}, justification = "written to by Stapler")
    private SignupInfo validateAccountCreationForm(StaplerRequest2 req, boolean validateCaptcha) {
        SignupInfo si = new SignupInfo(req);
        if (validateCaptcha && !validateCaptcha(si.captcha)) {
            si.errors.put("captcha", Messages.HudsonPrivateSecurityRealm_CreateAccount_TextNotMatchWordInImage());
        }
        if (si.username == null || si.username.isEmpty()) {
            si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameRequired());
        } else if (!containsOnlyAcceptableCharacters(si.username)) {
            if (ID_REGEX == null) {
                si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameInvalidCharacters());
            } else {
                si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameInvalidCharactersCustom(ID_REGEX));
            }
        } else {
            User user = User.getById(si.username, false);
            if (null != user && user.getProperty(Details.class) != null) {
                si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameAlreadyTaken());
            }
        }
        if (si.password1 != null && !si.password1.equals(si.password2)) {
            si.errors.put("password1", Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordNotMatch());
        }
        if (si.password1 == null || si.password1.isEmpty()) {
            si.errors.put("password1", Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordRequired());
        }
        try {
            PASSWORD_HASH_ENCODER.encode2(si.password1);
        } catch (RuntimeException ex) {
            si.errors.put("password1", ex.getMessage());
        }
        if (!si.errors.containsKey("password1") && si.password1 != null && !si.password1.isEmpty()) {
            try {
                this.passwordComplexityRule.validate(si.password1);
            } catch (PasswordComplexityException e) {
                si.errors.put("password1", e.getMessage());
            }
        }
        if (si.fullname == null || si.fullname.isEmpty()) {
            si.fullname = si.username;
        }
        if (isMailerPluginPresent() && (si.email == null || !si.email.contains("@"))) {
            si.errors.put("email", Messages.HudsonPrivateSecurityRealm_CreateAccount_InvalidEmailAddress());
        }
        if (!User.isIdOrFullnameAllowed(si.username)) {
            si.errors.put("username", Messages.User_IllegalUsername(si.username));
        }
        if (!User.isIdOrFullnameAllowed(si.fullname)) {
            si.errors.put("fullname", Messages.User_IllegalFullname(si.fullname));
        }
        req.setAttribute("data", si);
        return si;
    }

    private User createAccount(SignupInfo si) throws IOException {
        if (!si.errors.isEmpty()) {
            String messages = getErrorMessages(si);
            throw new IllegalArgumentException("invalid signup info passed to createAccount(si): " + messages);
        }
        User user = createAccount(si.username, si.password1);
        user.setFullName(si.fullname);
        if (isMailerPluginPresent()) {
            try {
                Class<?> up = Jenkins.get().pluginManager.uberClassLoader.loadClass("hudson.tasks.Mailer$UserProperty");
                Constructor<?> c = up.getDeclaredConstructor(String.class);
                user.addProperty((UserProperty) c.newInstance(si.email));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        user.save();
        return user;
    }

    private boolean containsOnlyAcceptableCharacters(@NonNull String value) {
        return value.matches((String) Objects.requireNonNullElse(ID_REGEX, DEFAULT_ID_REGEX));
    }

    @Restricted({NoExternalUse.class})
    public boolean isMailerPluginPresent() {
        try {
            return null != Jenkins.get().pluginManager.uberClassLoader.loadClass("hudson.tasks.Mailer$UserProperty");
        } catch (ClassNotFoundException e) {
            LOGGER.finer("Mailer plugin not present");
            return false;
        }
    }

    public User createAccount(String userName, String password) throws IOException {
        User user = User.getById(userName, true);
        user.addProperty(Details.fromPlainPassword(password));
        SecurityListener.fireUserCreated(user.getId());
        return user;
    }

    public User createAccountWithHashedPassword(String userName, String hashedPassword) throws IOException {
        String message;
        if (!PASSWORD_ENCODER.isPasswordHashed(hashedPassword)) {
            if (hashedPassword == null) {
                message = "The hashed password cannot be null";
            } else if (hashedPassword.startsWith(getPasswordHeader())) {
                message = "The hashed password was hashed with the correct algorithm, but the format was not correct";
            } else {
                message = "The hashed password was hashed with an incorrect algorithm. Jenkins is expecting " + getPasswordHeader();
            }
            throw new IllegalArgumentException(message);
        }
        User user = User.getById(userName, true);
        user.addProperty(Details.fromHashedPassword(hashedPassword));
        SecurityListener.fireUserCreated(user.getId());
        return user;
    }

    public String getDisplayName() {
        return Messages.HudsonPrivateSecurityRealm_DisplayName();
    }

    public ACL getACL() {
        return Jenkins.get().getACL();
    }

    public void checkPermission(Permission permission) {
        Jenkins.get().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return Jenkins.get().hasPermission(permission);
    }

    public List<User> getAllUsers() {
        List<User> r = new ArrayList<>();
        for (User u : User.getAll()) {
            if (u.getProperty(Details.class) != null) {
                r.add(u);
            }
        }
        Collections.sort(r);
        return r;
    }

    @Restricted({NoExternalUse.class})
    public User getUser(String id) {
        return User.getById(id, User.ALLOW_USER_CREATION_VIA_URL && hasPermission(Jenkins.ADMINISTER));
    }

    /* loaded from: HudsonPrivateSecurityRealm$SignupInfo.class */
    public static final class SignupInfo {
        public String username;
        public String password1;
        public String password2;
        public String fullname;
        public String email;
        public String captcha;

        @SuppressFBWarnings(value = {"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"}, justification = "read by Stapler")
        public String errorMessage;
        public HashMap<String, String> errors = new HashMap<>();

        public SignupInfo() {
        }

        public SignupInfo(StaplerRequest2 req) {
            req.bindParameters(this);
        }

        public SignupInfo(FederatedLoginService.FederatedIdentity i) {
            this.username = i.getNickname();
            this.fullname = i.getFullName();
            this.email = i.getEmailAddress();
        }
    }

    /* loaded from: HudsonPrivateSecurityRealm$Details.class */
    public static final class Details extends UserProperty {
        private String passwordHash;

        @Deprecated
        private transient String password;

        private Details(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        static Details fromHashedPassword(String hashed) {
            return new Details(hashed);
        }

        static Details fromPlainPassword(String rawPassword) {
            return new Details(HudsonPrivateSecurityRealm.PASSWORD_ENCODER.encode(rawPassword));
        }

        public Collection<? extends GrantedAuthority> getAuthorities2() {
            return HudsonPrivateSecurityRealm.TEST_AUTHORITY;
        }

        @Deprecated
        public org.acegisecurity.GrantedAuthority[] getAuthorities() {
            return org.acegisecurity.GrantedAuthority.fromSpring(getAuthorities2());
        }

        public String getPassword() {
            return this.passwordHash;
        }

        public boolean isPasswordCorrect(String candidate) {
            return HudsonPrivateSecurityRealm.PASSWORD_ENCODER.matches(candidate, getPassword());
        }

        public String getProtectedPassword() {
            return Protector.protect(Stapler.getCurrentRequest2().getSession().getId() + ":" + getPassword());
        }

        public String getUsername() {
            return this.user.getId();
        }

        User getUser() {
            return this.user;
        }

        public boolean isAccountNonExpired() {
            return true;
        }

        public boolean isAccountNonLocked() {
            return true;
        }

        public boolean isCredentialsNonExpired() {
            return true;
        }

        public boolean isEnabled() {
            return true;
        }

        UserDetails asUserDetails() {
            return new UserDetailsImpl(getAuthorities2(), getPassword(), getUsername(), isAccountNonExpired(), isAccountNonLocked(), isCredentialsNonExpired(), isEnabled());
        }

        /* loaded from: HudsonPrivateSecurityRealm$Details$UserDetailsImpl.class */
        private static final class UserDetailsImpl implements UserDetails {
            private static final long serialVersionUID = 1;
            private final Collection<? extends GrantedAuthority> authorities;
            private final String password;
            private final String username;
            private final boolean accountNonExpired;
            private final boolean accountNonLocked;
            private final boolean credentialsNonExpired;
            private final boolean enabled;

            UserDetailsImpl(Collection<? extends GrantedAuthority> authorities, String password, String username, boolean accountNonExpired, boolean accountNonLocked, boolean credentialsNonExpired, boolean enabled) {
                this.authorities = authorities;
                this.password = password;
                this.username = username;
                this.accountNonExpired = accountNonExpired;
                this.accountNonLocked = accountNonLocked;
                this.credentialsNonExpired = credentialsNonExpired;
                this.enabled = enabled;
            }

            public Collection<? extends GrantedAuthority> getAuthorities() {
                return this.authorities;
            }

            public String getPassword() {
                return this.password;
            }

            public String getUsername() {
                return this.username;
            }

            public boolean isAccountNonExpired() {
                return this.accountNonExpired;
            }

            public boolean isAccountNonLocked() {
                return this.accountNonLocked;
            }

            public boolean isCredentialsNonExpired() {
                return this.credentialsNonExpired;
            }

            public boolean isEnabled() {
                return this.enabled;
            }

            public boolean equals(Object o) {
                return (o instanceof UserDetailsImpl) && ((UserDetailsImpl) o).getUsername().equals(getUsername());
            }

            public int hashCode() {
                return getUsername().hashCode();
            }
        }

        @Extension
        @Symbol({"password"})
        /* loaded from: HudsonPrivateSecurityRealm$Details$DescriptorImpl.class */
        public static final class DescriptorImpl extends UserPropertyDescriptor {
            @NonNull
            public String getDisplayName() {
                return Messages.HudsonPrivateSecurityRealm_Details_DisplayName();
            }

            /* renamed from: newInstance, reason: merged with bridge method [inline-methods] */
            public Details m67newInstance(StaplerRequest2 req, JSONObject formData) throws Descriptor.FormException {
                if (req == null) {
                    throw new Descriptor.FormException("Stapler request is missing in the call", "staplerRequest");
                }
                String pwd = Util.fixEmpty(req.getParameter("user.password"));
                String pwd2 = Util.fixEmpty(req.getParameter("user.password2"));
                if (pwd == null || pwd2 == null) {
                    throw new Descriptor.FormException("Please confirm the password by typing it twice", "user.password2");
                }
                String data = Protector.unprotect(pwd);
                String data2 = Protector.unprotect(pwd2);
                if ((data == null) != (data2 == null)) {
                    throw new Descriptor.FormException("Please confirm the password by typing it twice", "user.password2");
                }
                if (data != null && !MessageDigest.isEqual(data.getBytes(StandardCharsets.UTF_8), data2.getBytes(StandardCharsets.UTF_8))) {
                    throw new Descriptor.FormException("Please confirm the password by typing it twice", "user.password2");
                }
                if (data == null && !pwd.equals(pwd2)) {
                    throw new Descriptor.FormException("Please confirm the password by typing it twice", "user.password2");
                }
                if (data != null) {
                    String prefix = Stapler.getCurrentRequest2().getSession().getId() + ":";
                    if (data.startsWith(prefix)) {
                        return Details.fromHashedPassword(data.substring(prefix.length()));
                    }
                }
                try {
                    HudsonPrivateSecurityRealm.PASSWORD_HASH_ENCODER.encode2(pwd);
                    Describable securityRealm = Jenkins.get().getSecurityRealm();
                    if (securityRealm instanceof HudsonPrivateSecurityRealm) {
                        HudsonPrivateSecurityRealm hpsr = (HudsonPrivateSecurityRealm) securityRealm;
                        try {
                            hpsr.getPasswordComplexityRule().validate(pwd);
                        } catch (PasswordComplexityException e) {
                            throw new Descriptor.FormException(e.getMessage(), "user.password");
                        }
                    }
                    User user = (User) Util.getNearestAncestorOfTypeOrThrow(req, User.class);
                    UserSeedProperty userSeedProperty = user.getProperty(UserSeedProperty.class);
                    if (userSeedProperty != null) {
                        userSeedProperty.renewSeed();
                    }
                    return Details.fromPlainPassword(Util.fixNull(pwd));
                } catch (RuntimeException ex) {
                    throw new Descriptor.FormException(ex.getMessage(), "user.password");
                }
            }

            @POST
            public FormValidation doCheckPassword(@QueryParameter String value) {
                String password = Util.fixEmpty(value);
                if (password == null) {
                    return FormValidation.ok();
                }
                String data = Protector.unprotect(password);
                if (data != null) {
                    String prefix = Stapler.getCurrentRequest2().getSession().getId() + ":";
                    if (data.startsWith(prefix)) {
                        return FormValidation.ok();
                    }
                }
                Describable securityRealm = Jenkins.get().getSecurityRealm();
                if (securityRealm instanceof HudsonPrivateSecurityRealm) {
                    HudsonPrivateSecurityRealm hpsr = (HudsonPrivateSecurityRealm) securityRealm;
                    try {
                        hpsr.getPasswordComplexityRule().validate(password);
                    } catch (PasswordComplexityException e) {
                        return FormValidation.error(e.getMessage());
                    }
                }
                return FormValidation.ok();
            }

            public boolean isEnabled() {
                return Jenkins.get().getSecurityRealm() instanceof HudsonPrivateSecurityRealm;
            }

            public UserProperty newInstance(User user) {
                return null;
            }

            @NonNull
            public UserPropertyCategory getUserPropertyCategory() {
                return UserPropertyCategory.get(UserPropertyCategory.Security.class);
            }
        }
    }

    @Extension
    @Symbol({"localUsers"})
    /* loaded from: HudsonPrivateSecurityRealm$ManageUserLinks.class */
    public static final class ManageUserLinks extends ManagementLink {
        public String getIconFileName() {
            if (Jenkins.get().getSecurityRealm() instanceof HudsonPrivateSecurityRealm) {
                return "symbol-people";
            }
            return null;
        }

        public String getUrlName() {
            return "securityRealm/";
        }

        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_ManageUserLinks_DisplayName();
        }

        public String getDescription() {
            return Messages.HudsonPrivateSecurityRealm_ManageUserLinks_Description();
        }

        @NonNull
        public ManagementLink.Category getCategory() {
            return ManagementLink.Category.SECURITY;
        }
    }

    /* loaded from: HudsonPrivateSecurityRealm$JBCryptEncoder.class */
    static class JBCryptEncoder extends BCryptPasswordEncoder implements PasswordHashEncoder {

        @Restricted({NoExternalUse.class})
        private static int MAXIMUM_BCRYPT_LOG_ROUND = SystemProperties.getInteger(HudsonPrivateSecurityRealm.class.getName() + ".maximumBCryptLogRound", 18).intValue();
        private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2a\\$([0-9]{2})\\$.{53}$");

        JBCryptEncoder() {
        }

        public String encode2(CharSequence rawPassword) {
            try {
                return encode(rawPassword);
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage().equals("password cannot be more than 72 bytes")) {
                    if (rawPassword.toString().matches("\\A\\p{ASCII}+\\z")) {
                        throw new IllegalArgumentException(Messages.HudsonPrivateSecurityRealm_CreateAccount_BCrypt_PasswordTooLong_ASCII());
                    }
                    throw new IllegalArgumentException(Messages.HudsonPrivateSecurityRealm_CreateAccount_BCrypt_PasswordTooLong());
                }
                throw ex;
            }
        }

        public boolean isHashValid(String hash) {
            Matcher matcher = BCRYPT_PATTERN.matcher(hash);
            if (matcher.matches()) {
                String logNumOfRound = matcher.group(1);
                int logNumOfRoundInt = Integer.parseInt(logNumOfRound);
                if (logNumOfRoundInt > 0 && logNumOfRoundInt <= MAXIMUM_BCRYPT_LOG_ROUND) {
                    return true;
                }
                return false;
            }
            return false;
        }
    }

    /* loaded from: HudsonPrivateSecurityRealm$PBKDF2PasswordEncoder.class */
    static class PBKDF2PasswordEncoder implements PasswordHashEncoder {
        private static final String STRING_SEPARATION = ":";
        private static final int KEY_LENGTH_BITS = 512;
        private static final int SALT_LENGTH_BYTES = 16;
        private static final int ITTERATIONS = 210000;
        private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512";
        private volatile SecureRandom random;
        private static final Pattern PBKDF2_PATTERN = Pattern.compile("^\\$HMACSHA512\\:210000\\:[a-f0-9]{32}\\$[a-f0-9]{128}$");

        PBKDF2PasswordEncoder() {
        }

        public String encode(CharSequence rawPassword) {
            if (rawPassword == null) {
                throw new IllegalArgumentException("Null rawPassword cannot be encoded");
            }
            if (rawPassword.length() < HudsonPrivateSecurityRealm.FIPS_PASSWORD_LENGTH) {
                throw new IllegalArgumentException(Messages.HudsonPrivateSecurityRealm_CreateAccount_FIPS_PasswordLengthInvalid());
            }
            try {
                return generatePasswordHashWithPBKDF2(rawPassword);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException("Unable to generate password with PBKDF2WithHmacSHA512", e);
            }
        }

        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (rawPassword == null) {
                throw new IllegalArgumentException("Null rawPassword cannot be compared");
            }
            try {
                return validatePassword(rawPassword.toString(), encodedPassword);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException("Unable to check password with PBKDF2WithHmacSHA512", e);
            }
        }

        private String generatePasswordHashWithPBKDF2(CharSequence password) throws NoSuchAlgorithmException, InvalidKeySpecException {
            byte[] salt = generateSalt();
            PBEKeySpec spec = new PBEKeySpec(password.toString().toCharArray(), salt, ITTERATIONS, KEY_LENGTH_BITS);
            byte[] hash = generateSecretKey(spec);
            return "$HMACSHA512:210000:" + Util.toHexString(salt) + "$" + Util.toHexString(hash);
        }

        private static byte[] generateSecretKey(PBEKeySpec spec) throws NoSuchAlgorithmException, InvalidKeySpecException {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return secretKeyFactory.generateSecret(spec).getEncoded();
        }

        private SecureRandom secureRandom() {
            if (this.random == null) {
                synchronized (this) {
                    if (this.random == null) {
                        this.random = new SecureRandom();
                    }
                }
            }
            return this.random;
        }

        private byte[] generateSalt() {
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            secureRandom().nextBytes(salt);
            return salt;
        }

        public boolean isHashValid(String hash) {
            Matcher matcher = PBKDF2_PATTERN.matcher(hash);
            return matcher.matches();
        }

        private static boolean validatePassword(String password, String storedPassword) throws NoSuchAlgorithmException, InvalidKeySpecException {
            String[] parts = storedPassword.split("[:$]");
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = Util.fromHexString(parts[3]);
            byte[] hash = Util.fromHexString(parts[4]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
            byte[] generatedHashValue = generateSecretKey(spec);
            return MessageDigest.isEqual(hash, generatedHashValue);
        }
    }

    private static String getPasswordHeader() {
        return FIPS140.useCompliantAlgorithms() ? PBKDF2 : JBCRYPT;
    }

    /* loaded from: HudsonPrivateSecurityRealm$MultiPasswordEncoder.class */
    static class MultiPasswordEncoder implements PasswordEncoder {
        MultiPasswordEncoder() {
        }

        public String encode(CharSequence rawPassword) {
            return HudsonPrivateSecurityRealm.getPasswordHeader() + HudsonPrivateSecurityRealm.PASSWORD_HASH_ENCODER.encode(rawPassword);
        }

        public boolean matches(CharSequence rawPassword, String encPass) {
            if (isPasswordHashed(encPass)) {
                return HudsonPrivateSecurityRealm.PASSWORD_HASH_ENCODER.matches(rawPassword, encPass.substring(HudsonPrivateSecurityRealm.getPasswordHeader().length()));
            }
            return false;
        }

        public boolean isPasswordHashed(String password) {
            if (password == null) {
                return false;
            }
            if (password.startsWith(HudsonPrivateSecurityRealm.getPasswordHeader())) {
                return HudsonPrivateSecurityRealm.PASSWORD_HASH_ENCODER.isHashValid(password.substring(HudsonPrivateSecurityRealm.getPasswordHeader().length()));
            }
            if (password.startsWith(FIPS140.useCompliantAlgorithms() ? HudsonPrivateSecurityRealm.JBCRYPT : HudsonPrivateSecurityRealm.PBKDF2)) {
                HudsonPrivateSecurityRealm.LOGGER.log(Level.WARNING, "A password appears to be stored (or is attempting to be stored) that was created with a different hashing/encryption algorithm, check the FIPS-140 state of the system has not changed inadvertently");
                return false;
            }
            HudsonPrivateSecurityRealm.LOGGER.log(Level.FINE, "A password appears to be stored (or is attempting to be stored) that is not hashed/encrypted.");
            return false;
        }
    }

    @SuppressFBWarnings(value = {"PREDICTABLE_RANDOM"}, justification = "Doesn't need to be secure, we're just not hardcoding a 'wrong' password")
    private static String generatePassword() {
        String password = ((StringBuilder) new Random().ints(20L, 33, 127).mapToObj(i -> {
            return Character.valueOf((char) i);
        }).collect(StringBuilder::new, (v0, v1) -> {
            v0.appendCodePoint(v1);
        }, (v0, v1) -> {
            v0.append(v1);
        })).toString();
        return password;
    }

    @Extension
    @Symbol({"local"})
    /* loaded from: HudsonPrivateSecurityRealm$DescriptorImpl.class */
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        @NonNull
        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_DisplayName();
        }

        public FormValidation doCheckAllowsSignup(@QueryParameter boolean value) {
            if (value) {
                return FormValidation.warning(Messages.HudsonPrivateSecurityRealm_SignupWarning());
            }
            return FormValidation.ok();
        }

        public PasswordComplexityRule getDefaultPasswordComplexityRule() {
            return new NonePasswordComplexityRule();
        }
    }
}
