package org.kohsuke.stapler;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

/* loaded from: CrumbIssuer.class */
public abstract class CrumbIssuer {
    public static final CrumbIssuer DEFAULT = new CrumbIssuer() { // from class: org.kohsuke.stapler.CrumbIssuer.1
        @Override // org.kohsuke.stapler.CrumbIssuer
        public String issueCrumb(StaplerRequest2 request) {
            HttpSession s = request.getSession();
            String v = (String) s.getAttribute(CrumbIssuer.ATTRIBUTE_NAME);
            if (v != null) {
                return v;
            }
            String v2 = UUID.randomUUID().toString();
            s.setAttribute(CrumbIssuer.ATTRIBUTE_NAME, v2);
            return v2;
        }
    };
    private static final String ATTRIBUTE_NAME = CrumbIssuer.class.getName();

    public String issueCrumb(StaplerRequest2 request) {
        return (String) ReflectionUtils.ifOverridden(() -> {
            return issueCrumb(StaplerRequest.fromStaplerRequest2(request));
        }, CrumbIssuer.class, getClass(), "issueCrumb", new Class[]{StaplerRequest.class});
    }

    @Deprecated
    public String issueCrumb(StaplerRequest request) {
        return (String) ReflectionUtils.ifOverridden(() -> {
            return issueCrumb(StaplerRequest.toStaplerRequest2(request));
        }, CrumbIssuer.class, getClass(), "issueCrumb", new Class[]{StaplerRequest2.class});
    }

    public final String issueCrumb() {
        return issueCrumb(Stapler.getCurrentRequest2());
    }

    public HttpResponse doCrumb() {
        return HttpResponses.text(issueCrumb());
    }

    public void validateCrumb(StaplerRequest2 request, String submittedCrumb) {
        if (!issueCrumb(request).equals(submittedCrumb)) {
            throw new SecurityException("Request failed to pass the crumb test (try clearing your cookies)");
        }
    }

    @Deprecated
    public void validateCrumb(StaplerRequest request, String submittedCrumb) {
        validateCrumb(StaplerRequest.toStaplerRequest2(request), submittedCrumb);
    }
}
