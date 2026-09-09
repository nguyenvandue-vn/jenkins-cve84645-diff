package org.kohsuke.stapler;

/* loaded from: CrumbIssuer.class */
public abstract class CrumbIssuer {
    public static final CrumbIssuer NONE = new CrumbIssuer() { // from class: org.kohsuke.stapler.CrumbIssuer.1
        @Override // org.kohsuke.stapler.CrumbIssuer
        public String issueCrumb(StaplerRequest2 request) {
            return "";
        }

        @Override // org.kohsuke.stapler.CrumbIssuer
        public String getCrumbExpression() {
            return "''";
        }
    };

    public abstract String getCrumbExpression();

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
