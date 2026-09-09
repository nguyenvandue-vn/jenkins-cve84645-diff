package hudson.model;

import com.infradna.tool.bridge_method_injector.BridgeMethodsAdded;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.FileSystemProvisioner;
import hudson.Launcher;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerListener;
import hudson.slaves.EphemeralNode;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.OfflineCause;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.EnumConverter;
import hudson.util.TagCloud;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.Nodes;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONObject;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.ProtectedExternally;
import org.kohsuke.stapler.BindInterceptor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

@ExportedBean
@BridgeMethodsAdded
/* loaded from: Node.class */
public abstract class Node extends AbstractModelObject implements ReconfigurableDescribable<Node>, ExtensionPoint, AccessControlled, OnMaster, PersistenceRoot {
    private static final Logger LOGGER = Logger.getLogger(Node.class.getName());

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean SKIP_BUILD_CHECK_ON_FLYWEIGHTS = SystemProperties.getBoolean(Node.class.getName() + ".SKIP_BUILD_CHECK_ON_FLYWEIGHTS", true);
    protected volatile transient boolean holdOffLaunchUntilSave;
    private transient Nodes parent;
    private volatile OfflineCause temporaryOfflineCause;

    @Exported(visibility = 999)
    @NonNull
    public abstract String getNodeName();

    @Deprecated
    public abstract void setNodeName(String name);

    @Exported
    public abstract String getNodeDescription();

    public abstract Launcher createLauncher(TaskListener listener);

    @Exported
    public abstract int getNumExecutors();

    @Exported
    public abstract Mode getMode();

    @CheckForNull
    @Restricted({ProtectedExternally.class})
    protected abstract Computer createComputer();

    public abstract String getLabelString();

    @CheckForNull
    public abstract FilePath getWorkspaceFor(TopLevelItem item);

    @CheckForNull
    public abstract FilePath getRootPath();

    @NonNull
    public abstract DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties();

    @Override // 
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public abstract NodeDescriptor mo39getDescriptor();

    public abstract Callable<ClockDifference, IOException> getClockDifferenceCallable();

    public String getDisplayName() {
        return getNodeName();
    }

    public String getSearchUrl() {
        Computer c = toComputer();
        if (c != null) {
            return c.getUrl();
        }
        return "computer/" + Util.rawEncode(getNodeName());
    }

    public boolean isHoldOffLaunchUntilSave() {
        return this.holdOffLaunchUntilSave;
    }

    public void save() throws IOException {
        if (this.parent == null) {
            return;
        }
        if (this instanceof EphemeralNode) {
            Util.deleteRecursive(getRootDir());
        } else {
            if (BulkChange.contains(this)) {
                return;
            }
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        }
    }

    protected XmlFile getConfigFile() {
        return this.parent.getConfigFile(this);
    }

    @CheckForNull
    public final Computer toComputer() {
        AbstractCIBase ciBase = Jenkins.get();
        return ciBase.getComputer(this);
    }

    @CheckForNull
    public final VirtualChannel getChannel() {
        Computer c = toComputer();
        if (c == null) {
            return null;
        }
        return c.getChannel();
    }

    public boolean isAcceptingTasks() {
        return true;
    }

    public void onLoad(Nodes parent, String name) {
        this.parent = parent;
        setNodeName(name);
    }

    boolean isTemporarilyOffline() {
        return this.temporaryOfflineCause != null;
    }

    @Restricted({NoExternalUse.class})
    public void setTemporaryOfflineCause(OfflineCause cause) {
        try {
            if (this.temporaryOfflineCause != cause) {
                this.temporaryOfflineCause = cause;
                Jenkins.get().updateNode(this);
            }
            if (this.temporaryOfflineCause != null) {
                Listeners.notify(ComputerListener.class, false, l -> {
                    l.onTemporarilyOffline(toComputer(), this.temporaryOfflineCause);
                });
            } else {
                Listeners.notify(ComputerListener.class, false, l2 -> {
                    l2.onTemporarilyOnline(toComputer());
                });
            }
        } catch (IOException e) {
            LOGGER.warning("Unable to complete save, temporary offline status will not be persisted: " + e.getMessage());
        }
    }

    public OfflineCause getTemporaryOfflineCause() {
        return this.temporaryOfflineCause;
    }

    public TagCloud<LabelAtom> getLabelCloud() {
        return new TagCloud<>(getAssignedLabels(), (v0) -> {
            return v0.getTiedJobCount();
        });
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    protected Set<LabelAtom> getLabelAtomSet() {
        return Collections.unmodifiableSet(Label.parse(getLabelString()));
    }

    @Exported
    public Set<LabelAtom> getAssignedLabels() {
        Set<LabelAtom> r = new HashSet<>(getLabelAtomSet());
        r.add(m40getSelfLabel());
        r.addAll(getDynamicLabels());
        return Collections.unmodifiableSet(r);
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public Set<LabelAtom> drainLabelsToTrim() {
        return new HashSet(getAssignedLabels());
    }

    private HashSet<LabelAtom> getDynamicLabels() {
        HashSet<LabelAtom> result = new HashSet<>();
        Iterator it = LabelFinder.all().iterator();
        while (it.hasNext()) {
            LabelFinder labeler = (LabelFinder) it.next();
            for (Label label : labeler.findLabels(this)) {
                if (label instanceof LabelAtom) {
                    result.add((LabelAtom) label);
                }
            }
        }
        return result;
    }

    public void setLabelString(String labelString) throws IOException {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @WithBridgeMethods({Label.class})
    /* renamed from: getSelfLabel, reason: merged with bridge method [inline-methods] */
    public LabelAtom m40getSelfLabel() {
        return LabelAtom.get(getNodeName());
    }

    @Deprecated
    public CauseOfBlockage canTake(Queue.Task task) {
        return null;
    }

    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        CauseOfBlockage c;
        Label l = item.getAssignedLabel();
        if (l != null && !l.contains(this)) {
            return CauseOfBlockage.fromMessage(Messages._Node_LabelMissing(getDisplayName(), l));
        }
        if (l == null && getMode() == Mode.EXCLUSIVE && (!(item.task instanceof Queue.FlyweightTask) || (!(this instanceof Jenkins) && Jenkins.get().getNumExecutors() >= 1 && Jenkins.get().getMode() != Mode.EXCLUSIVE))) {
            return CauseOfBlockage.fromMessage(Messages._Node_BecauseNodeIsReserved(getDisplayName()));
        }
        Authentication identity = item.authenticate2();
        if ((!SKIP_BUILD_CHECK_ON_FLYWEIGHTS || !(item.task instanceof Queue.FlyweightTask)) && !hasPermission2(identity, Computer.BUILD)) {
            return CauseOfBlockage.fromMessage(Messages._Node_LackingBuildPermission(identity.getName(), getDisplayName()));
        }
        Iterator it = getNodeProperties().iterator();
        while (it.hasNext()) {
            NodeProperty prop = (NodeProperty) it.next();
            try {
                c = prop.canTake(item);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t, () -> {
                    return String.format("Exception evaluating if the node '%s' can take the task '%s'", getNodeName(), item.task.getName());
                });
                c = CauseOfBlockage.fromMessage(Messages._Queue_ExceptionCanTake());
            }
            if (c != null) {
                return c;
            }
        }
        if (!isAcceptingTasks()) {
            return new CauseOfBlockage.BecauseNodeIsNotAcceptingTasks(this);
        }
        return null;
    }

    @CheckForNull
    public FilePath createPath(String absolutePath) {
        VirtualChannel ch = getChannel();
        if (ch == null) {
            return null;
        }
        return new FilePath(ch, absolutePath);
    }

    @Deprecated
    public FileSystemProvisioner getFileSystemProvisioner() {
        return FileSystemProvisioner.DEFAULT;
    }

    @CheckForNull
    public <T extends NodeProperty> T getNodeProperty(Class<T> clazz) {
        Iterator it = getNodeProperties().iterator();
        while (it.hasNext()) {
            NodeProperty p = (NodeProperty) it.next();
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    @CheckForNull
    public NodeProperty getNodeProperty(String className) {
        Iterator it = getNodeProperties().iterator();
        while (it.hasNext()) {
            NodeProperty p = (NodeProperty) it.next();
            if (p.getClass().getName().equals(className)) {
                return p;
            }
        }
        return null;
    }

    public List<NodePropertyDescriptor> getNodePropertyDescriptors() {
        return NodeProperty.for_(this);
    }

    @NonNull
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }

    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public Node m38reconfigure(@NonNull final StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(Node.class, getClass(), "reconfigure", new Class[]{StaplerRequest.class, JSONObject.class})) {
            return m37reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        }
        return reconfigureImpl(req, form);
    }

    @Deprecated
    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public Node m37reconfigure(@NonNull final StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private Node reconfigureImpl(@NonNull final StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (form == null) {
            return null;
        }
        final JSONObject jsonForProperties = form.optJSONObject("nodeProperties");
        final AtomicReference<BindInterceptor> old = new AtomicReference<>();
        old.set(req.setBindInterceptor(new BindInterceptor() { // from class: hudson.model.Node.1
            public Object onConvert(Type targetType, Class targetTypeErasure, Object jsonSource) {
                if (jsonForProperties != jsonSource) {
                    return ((BindInterceptor) old.get()).onConvert(targetType, targetTypeErasure, jsonSource);
                }
                try {
                    DescribableList<NodeProperty<?>, NodePropertyDescriptor> tmp = new DescribableList<>(Saveable.NOOP, Node.this.getNodeProperties().toList());
                    tmp.rebuild(req, jsonForProperties, NodeProperty.all());
                    return tmp.toList();
                } catch (Descriptor.FormException | IOException e) {
                    throw new IllegalArgumentException((Throwable) e);
                }
            }
        }));
        try {
            Node newInstance = mo39getDescriptor().newInstance(req, form);
            req.setBindListener(old.get());
            return newInstance;
        } catch (Throwable th) {
            req.setBindListener(old.get());
            throw th;
        }
    }

    public ClockDifference getClockDifference() throws IOException, InterruptedException {
        VirtualChannel channel = getChannel();
        if (channel == null) {
            throw new IOException(getNodeName() + " is offline");
        }
        return (ClockDifference) channel.call(getClockDifferenceCallable());
    }

    /* loaded from: Node$Mode.class */
    public enum Mode {
        NORMAL(Messages._Node_Mode_NORMAL()),
        EXCLUSIVE(Messages._Node_Mode_EXCLUSIVE());

        private final Localizable description;

        static {
            Stapler.CONVERT_UTILS.register(new EnumConverter(), Mode.class);
        }

        public String getDescription() {
            return this.description.toString();
        }

        public String getName() {
            return name();
        }

        Mode(Localizable description) {
            this.description = description;
        }
    }

    public File getRootDir() {
        return getParent().getRootDirFor(this);
    }

    @NonNull
    private Nodes getParent() {
        if (this.parent == null) {
            throw new IllegalStateException("no parent set on " + getClass().getName() + "[" + getNodeName() + "]");
        }
        return this.parent;
    }
}
