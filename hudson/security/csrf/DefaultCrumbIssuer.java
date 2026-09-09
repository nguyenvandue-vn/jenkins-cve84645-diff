package hudson.security.csrf;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.AdministrativeMonitor;
import hudson.model.Descriptor;
import hudson.model.ModelObject;
import hudson.model.PersistentDescriptor;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.HexStringConfidentialKey;
import jenkins.util.ClientHttpRedirect;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.core.Authentication;

/* loaded from: DefaultCrumbIssuer.class */
public class DefaultCrumbIssuer extends CrumbIssuer {
    private transient MessageDigest md;
    private transient boolean excludeClientIPFromCrumb;

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean EXCLUDE_SESSION_ID = SystemProperties.getBoolean(DefaultCrumbIssuer.class.getName() + ".EXCLUDE_SESSION_ID");
    private static final Logger LOGGER = Logger.getLogger(DefaultCrumbIssuer.class.getName());

    @DataBoundConstructor
    public DefaultCrumbIssuer() {
        initializeMessageDigest();
        logSessionIdWarningIfNeeded();
    }

    @Deprecated
    public DefaultCrumbIssuer(boolean excludeClientIPFromCrumb) {
        this();
        this.excludeClientIPFromCrumb = excludeClientIPFromCrumb;
        logSessionIdWarningIfNeeded();
    }

    private void logSessionIdWarningIfNeeded() {
        if (EXCLUDE_SESSION_ID) {
            LOGGER.warning("Jenkins no longer uses the client IP address as part of CSRF protection. This controller is configured to also not use the session ID (hudson.security.csrf.DefaultCrumbIssuer.EXCLUDE_SESSION_ID), which is even less safe now. This option will be removed in future releases.");
        }
    }

    @Deprecated
    public boolean isExcludeClientIPFromCrumb() {
        return this.excludeClientIPFromCrumb;
    }

    private Object readResolve() {
        initializeMessageDigest();
        return this;
    }

    private synchronized void initializeMessageDigest() {
        try {
            this.md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            this.md = null;
            LOGGER.log(Level.SEVERE, e, () -> {
                return "Cannot find SHA-256 MessageDigest implementation.";
            });
        }
    }

    @Override // hudson.security.csrf.CrumbIssuer
    @SuppressFBWarnings(value = {"NM_WRONG_PACKAGE"}, justification = "false positive")
    protected synchronized String issueCrumb(ServletRequest request, String salt) {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            if (this.md != null) {
                StringBuilder buffer = new StringBuilder();
                Authentication a = Jenkins.getAuthentication2();
                buffer.append(a.getName());
                if (!EXCLUDE_SESSION_ID) {
                    buffer.append(';');
                    buffer.append(req.getSession().getId());
                }
                this.md.update(buffer.toString().getBytes(StandardCharsets.UTF_8));
                return Util.toHexString(this.md.digest(salt.getBytes(StandardCharsets.US_ASCII)));
            }
            return null;
        }
        return null;
    }

    @Override // hudson.security.csrf.CrumbIssuer
    public boolean validateCrumb(ServletRequest request, String salt, String crumb) {
        String newCrumb;
        if ((request instanceof HttpServletRequest) && (newCrumb = issueCrumb(request, salt)) != null && crumb != null) {
            return MessageDigest.isEqual(newCrumb.getBytes(StandardCharsets.US_ASCII), crumb.getBytes(StandardCharsets.US_ASCII));
        }
        return false;
    }

    @Extension
    @Symbol({"standard"})
    /* loaded from: DefaultCrumbIssuer$DescriptorImpl.class */
    public static final class DescriptorImpl extends CrumbIssuerDescriptor<DefaultCrumbIssuer> implements ModelObject, PersistentDescriptor {
        private static final HexStringConfidentialKey CRUMB_SALT = new HexStringConfidentialKey(Jenkins.class, "crumbSalt", 16);

        public DescriptorImpl() {
            super(CRUMB_SALT.get(), SystemProperties.getString("hudson.security.csrf.requestfield", CrumbIssuer.DEFAULT_CRUMB_NAME));
        }

        @NonNull
        public String getDisplayName() {
            return Messages.DefaultCrumbIssuer_DisplayName();
        }

        /* renamed from: newInstance, reason: merged with bridge method [inline-methods] */
        public DefaultCrumbIssuer m77newInstance(StaplerRequest2 req, JSONObject formData) throws Descriptor.FormException {
            if (req == null) {
                throw new Descriptor.FormException("DefaultCrumbIssuer new instance method is called for null Stapler request. Such call is prohibited.", "req");
            }
            return (DefaultCrumbIssuer) req.bindJSON(DefaultCrumbIssuer.class, formData);
        }
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: DefaultCrumbIssuer$ExcludeSessionIdAdministrativeMonitor.class */
    public static class ExcludeSessionIdAdministrativeMonitor extends AdministrativeMonitor {
        public boolean isActivated() {
            CrumbIssuer crumbIssuer = Jenkins.get().getCrumbIssuer();
            if (crumbIssuer instanceof DefaultCrumbIssuer) {
                return DefaultCrumbIssuer.EXCLUDE_SESSION_ID;
            }
            return false;
        }

        public boolean isSecurity() {
            return true;
        }

        public String getDisplayName() {
            return "Warn when session ID is excluded from Default Crumb Issuer";
        }

        @POST
        public HttpResponse doAct(StaplerRequest2 req, @QueryParameter String learn, @QueryParameter String dismiss) throws IOException, ServletException {
            if (learn != null) {
                return new ClientHttpRedirect("https://www.jenkins.io/redirect/csrf-protection/");
            }
            if (dismiss != null) {
                disable(true);
            }
            return HttpResponses.forwardToPreviousPage();
        }
    }
}
