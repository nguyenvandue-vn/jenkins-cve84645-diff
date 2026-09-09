package hudson.slaves;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ReconfigurableDescribable;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.tools.PropertyDescriptor;
import java.io.IOException;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.XStreamNotDeserializable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/* loaded from: NodeProperty.class */
public abstract class NodeProperty<N extends Node> implements ReconfigurableDescribable<NodeProperty<?>>, ExtensionPoint {

    @XStreamNotDeserializable
    protected transient N node;

    protected void setNode(N node) {
        this.node = node;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public NodePropertyDescriptor m82getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Deprecated
    public CauseOfBlockage canTake(Queue.Task task) {
        return null;
    }

    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        return canTake(item.task);
    }

    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment(this) { // from class: hudson.slaves.NodeProperty.1
        };
    }

    public void buildEnvVars(@NonNull EnvVars env, @NonNull TaskListener listener) throws IOException, InterruptedException {
    }

    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public NodeProperty<?> m81reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(NodeProperty.class, getClass(), "reconfigure", new Class[]{StaplerRequest.class, JSONObject.class})) {
            return m80reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        }
        return reconfigureImpl(req, form);
    }

    @Deprecated
    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public NodeProperty<?> m80reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private NodeProperty<?> reconfigureImpl(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (form == null) {
            return null;
        }
        return m82getDescriptor().newInstance(req, form);
    }

    public static DescriptorExtensionList<NodeProperty<?>, NodePropertyDescriptor> all() {
        return Jenkins.get().getDescriptorList(NodeProperty.class);
    }

    public static List<NodePropertyDescriptor> for_(Node node) {
        return PropertyDescriptor.for_(all(), node);
    }
}
