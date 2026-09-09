package hudson.scm;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import hudson.model.TaskAction;
import hudson.security.ACL;
import hudson.security.Permission;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import jenkins.model.RunAction2;
import jenkins.security.XStreamNotDeserializable;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;

/* loaded from: AbstractScmTagAction.class */
public abstract class AbstractScmTagAction extends TaskAction implements BuildBadgeAction, RunAction2 {

    @XStreamNotDeserializable
    private transient Run<?, ?> run;

    @XStreamNotDeserializable
    @Deprecated
    protected transient AbstractBuild build;

    public abstract boolean isTagged();

    protected AbstractScmTagAction(Run<?, ?> run) {
        this.run = run;
        this.build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
    }

    @Deprecated
    protected AbstractScmTagAction(AbstractBuild build) {
        this((Run<?, ?>) build);
    }

    public final String getUrlName() {
        return "tagBuild";
    }

    protected Permission getPermission() {
        return SCM.TAG;
    }

    public Run<?, ?> getRun() {
        return this.run;
    }

    @Deprecated
    public AbstractBuild getBuild() {
        return this.build;
    }

    public String getTooltip() {
        return null;
    }

    protected ACL getACL() {
        return this.run.getACL();
    }

    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(AbstractScmTagAction.class, getClass(), "doIndex", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                doIndex(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
                return;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
        doIndexImpl(req, rsp);
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doIndexImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doIndexImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        req.getView(this, chooseAction()).forward(req, rsp);
    }

    protected synchronized String chooseAction() {
        if (this.workerThread != null) {
            return "inProgress.jelly";
        }
        return "tagForm.jelly";
    }

    public void onAttached(Run<?, ?> r) {
    }

    public void onLoad(Run<?, ?> r) {
        this.run = r;
        this.build = this.run instanceof AbstractBuild ? (AbstractBuild) this.run : null;
    }
}
