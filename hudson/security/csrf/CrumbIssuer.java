package hudson.security.csrf;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.init.Initializer;
import hudson.model.Api;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.tasks.Maven;
import hudson.util.MultipartFormDataParser;
import io.jenkins.servlet.ServletRequestWrapper;
import io.jenkins.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.SwitchBootstraps;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerAccessibleType;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
@StaplerAccessibleType
/* loaded from: CrumbIssuer.class */
public abstract class CrumbIssuer implements Describable<CrumbIssuer>, ExtensionPoint {
    private static final String CRUMB_ATTRIBUTE = CrumbIssuer.class.getName() + "_crumb";

    @Restricted({NoExternalUse.class})
    public static final String DEFAULT_CRUMB_NAME = "Jenkins-Crumb";

    @Exported
    public String getCrumbRequestField() {
        return m74getDescriptor().getCrumbRequestField();
    }

    @Exported
    public String getCrumb() {
        return getCrumb((ServletRequest) Stapler.getCurrentRequest2());
    }

    public String getCrumb(ServletRequest request) {
        String crumb = null;
        if (request != null) {
            crumb = (String) request.getAttribute(CRUMB_ATTRIBUTE);
        }
        if (crumb == null) {
            crumb = issueCrumb(request, m74getDescriptor().getCrumbSalt());
            if (request != null) {
                if (crumb != null && !crumb.isEmpty()) {
                    request.setAttribute(CRUMB_ATTRIBUTE, crumb);
                } else {
                    request.removeAttribute(CRUMB_ATTRIBUTE);
                }
            }
        }
        return crumb;
    }

    @Deprecated
    public String getCrumb(javax.servlet.ServletRequest request) {
        return getCrumb(request != null ? wrap(request) : null);
    }

    protected String issueCrumb(ServletRequest request, String salt) {
        return (String) Util.ifOverridden(() -> {
            return issueCrumb(request != null ? wrap(request) : null, salt);
        }, CrumbIssuer.class, getClass(), "issueCrumb", new Class[]{javax.servlet.ServletRequest.class, String.class});
    }

    @Deprecated
    protected String issueCrumb(javax.servlet.ServletRequest request, String salt) {
        return (String) Util.ifOverridden(() -> {
            return issueCrumb(request != null ? wrap(request) : null, salt);
        }, CrumbIssuer.class, getClass(), "issueCrumb", new Class[]{ServletRequest.class, String.class});
    }

    public boolean validateCrumb(ServletRequest request) {
        CrumbIssuerDescriptor<CrumbIssuer> desc = m74getDescriptor();
        String crumbField = desc.getCrumbRequestField();
        String crumbSalt = desc.getCrumbSalt();
        return validateCrumb(request, crumbSalt, request.getParameter(crumbField));
    }

    public boolean validateCrumb(ServletRequest request, MultipartFormDataParser parser) {
        CrumbIssuerDescriptor<CrumbIssuer> desc = m74getDescriptor();
        String crumbField = desc.getCrumbRequestField();
        String crumbSalt = desc.getCrumbSalt();
        return validateCrumb(request, crumbSalt, parser.get(crumbField));
    }

    @Deprecated
    public boolean validateCrumb(javax.servlet.ServletRequest request, MultipartFormDataParser parser) {
        return validateCrumb(request != null ? wrap(request) : null, parser);
    }

    private static ServletRequest wrap(@NonNull javax.servlet.ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            return HttpServletRequestWrapper.toJakartaHttpServletRequest(httpRequest);
        }
        return ServletRequestWrapper.toJakartaServletRequest(request);
    }

    public boolean validateCrumb(ServletRequest request, String salt, String crumb) {
        return ((Boolean) Util.ifOverridden(() -> {
            return Boolean.valueOf(validateCrumb(request != null ? wrap(request) : null, salt, crumb));
        }, CrumbIssuer.class, getClass(), "validateCrumb", new Class[]{javax.servlet.ServletRequest.class, String.class, String.class})).booleanValue();
    }

    private static javax.servlet.ServletRequest wrap(@NonNull ServletRequest request) {
        if (request instanceof jakarta.servlet.http.HttpServletRequest) {
            jakarta.servlet.http.HttpServletRequest httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
            return HttpServletRequestWrapper.fromJakartaHttpServletRequest(httpRequest);
        }
        return ServletRequestWrapper.fromJakartaServletRequest(request);
    }

    @Deprecated
    public boolean validateCrumb(javax.servlet.ServletRequest request, String salt, String crumb) {
        return ((Boolean) Util.ifOverridden(() -> {
            return Boolean.valueOf(validateCrumb(request != null ? wrap(request) : null, salt, crumb));
        }, CrumbIssuer.class, getClass(), "validateCrumb", new Class[]{ServletRequest.class, String.class, String.class})).booleanValue();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public CrumbIssuerDescriptor<CrumbIssuer> m74getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<CrumbIssuer, Descriptor<CrumbIssuer>> all() {
        return Jenkins.get().getDescriptorList(CrumbIssuer.class);
    }

    public Api getApi() {
        return new RestrictedApi(this);
    }

    @Initializer
    public static void initStaplerCrumbIssuer() {
        WebApp.get(Jenkins.get().getServletContext()).setCrumbIssuer(new org.kohsuke.stapler.CrumbIssuer() { // from class: hudson.security.csrf.CrumbIssuer.1
            public String issueCrumb(StaplerRequest2 request) {
                CrumbIssuer ci = Jenkins.get().getCrumbIssuer();
                return ci != null ? ci.getCrumb((ServletRequest) request) : DEFAULT.issueCrumb(request);
            }

            public void validateCrumb(StaplerRequest2 request, String submittedCrumb) {
                CrumbIssuer ci = Jenkins.get().getCrumbIssuer();
                if (ci == null) {
                    DEFAULT.validateCrumb(request, submittedCrumb);
                } else if (!ci.validateCrumb((ServletRequest) request, ci.m74getDescriptor().getCrumbSalt(), submittedCrumb)) {
                    throw new SecurityException("Crumb didn't match");
                }
            }
        });
    }

    @Restricted({NoExternalUse.class})
    /* loaded from: CrumbIssuer$RestrictedApi.class */
    public static class RestrictedApi extends Api {
        RestrictedApi(CrumbIssuer instance) {
            super(instance);
        }

        public void doXml(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String xpath, @QueryParameter String wrapper, @QueryParameter String tree, @QueryParameter int depth) throws IOException, ServletException {
            String text;
            setHeaders(rsp);
            CrumbIssuer ci = (CrumbIssuer) this.bean;
            switch ((int) SwitchBootstraps.typeSwitch(MethodHandles.lookup(), "typeSwitch", MethodType.methodType(Integer.TYPE, Object.class, Integer.TYPE), "/*/crumbRequestField/text()", "/*/crumb/text()", "concat(//crumbRequestField,\":\",//crumb)", "concat(//crumbRequestField,'=',//crumb)").dynamicInvoker().invoke(xpath, 0) /* invoke-custom */) {
                case -1:
                default:
                    text = null;
                    break;
                case Maven.MavenInstallation.MAVEN_20 /* 0 */:
                    text = ci.getCrumbRequestField();
                    break;
                case Maven.MavenInstallation.MAVEN_21 /* 1 */:
                    text = ci.getCrumb();
                    break;
                case Maven.MavenInstallation.MAVEN_30 /* 2 */:
                    text = ci.getCrumbRequestField() + ":" + ci.getCrumb();
                    break;
                case 3:
                    if (ci.getCrumbRequestField().startsWith(".") || ci.getCrumbRequestField().contains("-")) {
                        text = ci.getCrumbRequestField() + "=" + ci.getCrumb();
                        break;
                    } else {
                        text = null;
                        break;
                    }
                    break;
            }
            if (text != null) {
                ServletOutputStream outputStream = rsp.getOutputStream();
                try {
                    rsp.setContentType("text/plain;charset=UTF-8");
                    outputStream.write(text.getBytes(StandardCharsets.UTF_8));
                    if (outputStream != null) {
                        outputStream.close();
                        return;
                    }
                    return;
                } catch (Throwable th) {
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            super.doXml(req, rsp, xpath, wrapper, tree, depth);
        }
    }
}
