package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepMonitor;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
/* loaded from: JobProperty.class */
public abstract class JobProperty<J extends Job<?, ?>> implements ReconfigurableDescribable<JobProperty<?>>, BuildStep, ExtensionPoint {
    protected transient J owner;

    protected void setOwner(J owner) {
        this.owner = owner;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public JobPropertyDescriptor m33getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Deprecated
    public Action getJobAction(J job) {
        return null;
    }

    @NonNull
    public Collection<? extends Action> getJobActions(J job) {
        Action a = getJobAction(job);
        return a == null ? Collections.emptyList() : List.of(a);
    }

    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        return true;
    }

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public final Action getProjectAction(AbstractProject<?, ?> project) {
        return getJobAction(project);
    }

    @NonNull
    public final Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return getJobActions(project);
    }

    public Collection<?> getJobOverrides() {
        return Collections.emptyList();
    }

    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public JobProperty<?> m32reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(JobProperty.class, getClass(), "reconfigure", new Class[]{StaplerRequest.class, JSONObject.class})) {
            return m31reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        }
        return reconfigureImpl(req, form);
    }

    @Deprecated
    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public JobProperty<?> m31reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private JobProperty<?> reconfigureImpl(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (form == null) {
            return null;
        }
        return m33getDescriptor().newInstance(req, form);
    }

    public Collection<? extends SubTask> getSubTasks() {
        return Collections.emptyList();
    }
}
