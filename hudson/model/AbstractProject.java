package hudson.model;

import com.infradna.tool.bridge_method_injector.BridgeMethodsAdded;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.cli.declarative.CLIResolver;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.model.labels.LabelExpression;
import hudson.model.listeners.SCMPollListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.SubTask;
import hudson.model.queue.SubTaskContributor;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCMS;
import hudson.search.SearchIndexBuilder;
import hudson.security.Permission;
import hudson.slaves.WorkspaceList;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Publisher;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.BlockedBecauseOfBuildInProgress;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.Uptime;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.scm.DefaultSCMCheckoutStrategyImpl;
import jenkins.scm.SCMCheckoutStrategy;
import jenkins.scm.SCMCheckoutStrategyDescriptor;
import jenkins.scm.SCMDecisionHandler;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

@BridgeMethodsAdded
/* loaded from: AbstractProject.class */
public abstract class AbstractProject<P extends AbstractProject<P, R>, R extends AbstractBuild<P, R>> extends Job<P, R> implements BuildableItem, LazyBuildMixIn.LazyLoadingJob<P, R>, ParameterizedJobMixIn.ParameterizedJob<P, R> {
    private volatile SCM scm;
    private volatile SCMCheckoutStrategy scmCheckoutStrategy;
    private volatile transient SCMRevisionState pollingBaseline;
    private transient LazyBuildMixIn<P, R> buildMixIn;

    @Restricted({NoExternalUse.class})
    protected transient RunMap<R> builds;
    private volatile Integer quietPeriod;
    private volatile Integer scmCheckoutRetryCount;
    private String assignedNode;
    private volatile boolean canRoam;
    protected volatile boolean disabled;
    protected volatile boolean blockBuildWhenDownstreamBuilding;
    protected volatile boolean blockBuildWhenUpstreamBuilding;
    private volatile String jdk;
    private volatile BuildAuthorizationToken authToken;
    protected volatile DescribableList<Trigger<?>, TriggerDescriptor> triggers;
    protected volatile transient List<Action> transientActions;
    private boolean concurrentBuild;
    private String customWorkspace;
    private static final AtomicReferenceFieldUpdater<AbstractProject, DescribableList> triggersUpdater = AtomicReferenceFieldUpdater.newUpdater(AbstractProject.class, DescribableList.class, "triggers");
    private static final Comparator<Integer> REVERSE_INTEGER_COMPARATOR = Comparator.reverseOrder();
    private static final Logger LOGGER = Logger.getLogger(AbstractProject.class.getName());

    @Deprecated
    public static final Permission ABORT = CANCEL;

    @Deprecated
    public static final AlternativeUiTextProvider.Message<AbstractProject> BUILD_NOW_TEXT = new AlternativeUiTextProvider.Message<>();

    /* loaded from: AbstractProject$WorkspaceOfflineReason.class */
    enum WorkspaceOfflineReason {
        nonexisting_workspace,
        builton_node_gone,
        builton_node_no_executors,
        all_suitable_nodes_are_offline,
        use_ondemand_slave
    }

    public abstract DescribableList<Publisher, Descriptor<Publisher>> getPublishersList();

    protected abstract Class<R> getBuildClass();

    public abstract boolean isFingerprintConfigured();

    /* renamed from: scheduleBuild2, reason: collision with other method in class */
    public /* bridge */ /* synthetic */ Future m17scheduleBuild2(int i, Cause cause, Collection collection) {
        return scheduleBuild2(i, cause, (Collection<? extends Action>) collection);
    }

    protected AbstractProject(ItemGroup parent, String name) {
        super(parent, name);
        this.scm = new NullSCM();
        this.pollingBaseline = null;
        this.quietPeriod = null;
        this.scmCheckoutRetryCount = null;
        this.blockBuildWhenDownstreamBuilding = false;
        this.blockBuildWhenUpstreamBuilding = false;
        this.authToken = null;
        this.triggers = new DescribableList<>(this);
        this.transientActions = new Vector();
        this.buildMixIn = createBuildMixIn();
        this.builds = this.buildMixIn.getRunMap();
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null && !j.getNodes().isEmpty()) {
            this.canRoam = true;
        }
    }

    private LazyBuildMixIn<P, R> createBuildMixIn() {
        return (LazyBuildMixIn<P, R>) new LazyBuildMixIn<P, R>() { // from class: hudson.model.AbstractProject.1
            /* JADX INFO: Access modifiers changed from: protected */
            /* renamed from: asJob, reason: merged with bridge method [inline-methods] */
            public P m20asJob() {
                return (P) AbstractProject.this;
            }

            protected Class<R> getBuildClass() {
                return AbstractProject.this.getBuildClass();
            }
        };
    }

    public LazyBuildMixIn<P, R> getLazyBuildMixIn() {
        return this.buildMixIn;
    }

    @Override // hudson.model.Job, hudson.model.AbstractItem
    public synchronized void save() throws IOException {
        super.save();
        updateTransientActions();
    }

    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        this.buildMixIn.onCreatedFromScratch();
        this.builds = this.buildMixIn.getRunMap();
        updateTransientActions();
    }

    @Override // hudson.model.Job, hudson.model.AbstractItem
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if (this.buildMixIn == null) {
            this.buildMixIn = createBuildMixIn();
        }
        this.buildMixIn.onLoad(parent, name);
        this.builds = this.buildMixIn.getRunMap();
        m15triggers().setOwner(this);
        Iterator it = m15triggers().iterator();
        while (it.hasNext()) {
            Trigger t = (Trigger) it.next();
            try {
                t.start(this, Items.currentlyUpdatingByXml());
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "could not start trigger while loading project '" + getFullName() + "'", e);
            }
        }
        if (this.scm == null) {
            this.scm = new NullSCM();
        }
        if (this.transientActions == null) {
            this.transientActions = new Vector();
        }
        updateTransientActions();
    }

    /* JADX INFO: Access modifiers changed from: protected */
    @WithBridgeMethods({List.class})
    /* renamed from: triggers, reason: merged with bridge method [inline-methods] */
    public DescribableList<Trigger<?>, TriggerDescriptor> m15triggers() {
        if (this.triggers == null) {
            triggersUpdater.compareAndSet(this, null, new DescribableList(this));
        }
        return this.triggers;
    }

    @Override // hudson.model.Job
    @NonNull
    public EnvVars getEnvironment(@CheckForNull Node node, @NonNull TaskListener listener) throws IOException, InterruptedException {
        EnvVars env = super.getEnvironment(node, listener);
        JDK jdkTool = getJDK();
        if (jdkTool != null) {
            if (node != null) {
                jdkTool = jdkTool.m24forNode(node, listener);
            }
            jdkTool.buildEnvVars(env);
        } else if (!JDK.isDefaultName(this.jdk)) {
            listener.getLogger().println("No JDK named ‘" + this.jdk + "’ found");
        }
        return env;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // hudson.model.AbstractItem
    protected void performDelete() throws IOException, InterruptedException {
        if (supportsMakeDisabled()) {
            setDisabled(true);
            Jenkins.get().getQueue().cancel((Queue.Task) this);
        }
        FilePath ws = getWorkspace();
        if (ws != null) {
            Node on = getLastBuiltOn();
            getScm().processWorkspaceBeforeDeletion(this, ws, on);
        }
        super.performDelete();
    }

    @Exported
    public boolean isConcurrentBuild() {
        return this.concurrentBuild;
    }

    public void setConcurrentBuild(boolean b) throws IOException {
        this.concurrentBuild = b;
        save();
    }

    @CheckForNull
    public Label getAssignedLabel() {
        if (this.canRoam) {
            return null;
        }
        if (this.assignedNode == null) {
            return Jenkins.get().getSelfLabel();
        }
        return Jenkins.get().getLabel(this.assignedNode);
    }

    public Set<Label> getRelevantLabels() {
        return Collections.singleton(getAssignedLabel());
    }

    @Exported(name = "labelExpression")
    public String getAssignedLabelString() {
        if (this.canRoam || this.assignedNode == null) {
            return null;
        }
        try {
            Label.parseExpression(this.assignedNode);
            return this.assignedNode;
        } catch (IllegalArgumentException e) {
            return LabelAtom.escape(this.assignedNode);
        }
    }

    public void setAssignedLabel(Label l) throws IOException {
        if (l == null) {
            this.canRoam = true;
            this.assignedNode = null;
        } else {
            this.canRoam = false;
            if (l == Jenkins.get().getSelfLabel()) {
                this.assignedNode = null;
            } else {
                this.assignedNode = l.getExpression();
            }
        }
        save();
    }

    public void setAssignedNode(Node l) throws IOException {
        setAssignedLabel(l.m40getSelfLabel());
    }

    @Override // hudson.model.Job, hudson.model.AbstractItem
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.AbstractProject_Pronoun());
    }

    public String getBuildNowText() {
        return AlternativeUiTextProvider.get(BUILD_NOW_TEXT, this, super.getBuildNowText());
    }

    /* JADX WARN: Multi-variable type inference failed */
    public AbstractProject<?, ?> getRootProject() {
        if (this instanceof TopLevelItem) {
            return this;
        }
        AbstractProject parent = m3getParent();
        if (parent instanceof AbstractProject) {
            return parent.getRootProject();
        }
        return this;
    }

    @Deprecated
    public final FilePath getWorkspace() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        if (b != null) {
            return b.getWorkspace();
        }
        return null;
    }

    @CheckForNull
    private AbstractBuild getBuildForDeprecatedMethods() {
        Executor e = Executor.currentExecutor();
        if (e != null) {
            AbstractBuild currentExecutable = e.getCurrentExecutable();
            if (currentExecutable instanceof AbstractBuild) {
                AbstractBuild b = currentExecutable;
                if (b.getProject() == this) {
                    return b;
                }
            }
        }
        return mo6getLastBuild();
    }

    @CheckForNull
    public final FilePath getSomeWorkspace() {
        R b = getSomeBuildWithWorkspace();
        if (b != null) {
            return b.getWorkspace();
        }
        Iterator it = ExtensionList.lookup(WorkspaceBrowser.class).iterator();
        while (it.hasNext()) {
            WorkspaceBrowser browser = (WorkspaceBrowser) it.next();
            FilePath f = browser.getWorkspace(this);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    public final R getSomeBuildWithWorkspace() {
        R mo6getLastBuild = mo6getLastBuild();
        while (true) {
            R r = mo6getLastBuild;
            if (r != null) {
                if (r.getWorkspace() != null) {
                    return r;
                }
                mo6getLastBuild = (R) r.getPreviousBuild();
            } else {
                return null;
            }
        }
    }

    private R getSomeBuildWithExistingWorkspace() throws IOException, InterruptedException {
        R mo6getLastBuild = mo6getLastBuild();
        while (true) {
            R r = mo6getLastBuild;
            if (r != null) {
                FilePath workspace = r.getWorkspace();
                if (workspace != null && workspace.exists()) {
                    return r;
                }
                mo6getLastBuild = (R) r.getPreviousBuild();
            } else {
                return null;
            }
        }
    }

    @Deprecated
    public FilePath getModuleRoot() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        if (b != null) {
            return b.getModuleRoot();
        }
        return null;
    }

    @Deprecated
    public FilePath[] getModuleRoots() {
        AbstractBuild b = getBuildForDeprecatedMethods();
        if (b != null) {
            return b.getModuleRoots();
        }
        return null;
    }

    public int getQuietPeriod() {
        return this.quietPeriod != null ? this.quietPeriod.intValue() : Jenkins.get().getQuietPeriod();
    }

    public SCMCheckoutStrategy getScmCheckoutStrategy() {
        return this.scmCheckoutStrategy == null ? new DefaultSCMCheckoutStrategyImpl() : this.scmCheckoutStrategy;
    }

    public void setScmCheckoutStrategy(SCMCheckoutStrategy scmCheckoutStrategy) throws IOException {
        this.scmCheckoutStrategy = scmCheckoutStrategy;
        save();
    }

    public int getScmCheckoutRetryCount() {
        return this.scmCheckoutRetryCount != null ? this.scmCheckoutRetryCount.intValue() : Jenkins.get().getScmCheckoutRetryCount();
    }

    public boolean getHasCustomQuietPeriod() {
        return this.quietPeriod != null;
    }

    public void setQuietPeriod(Integer seconds) throws IOException {
        this.quietPeriod = seconds;
        save();
    }

    public boolean hasCustomScmCheckoutRetryCount() {
        return this.scmCheckoutRetryCount != null;
    }

    @Override // hudson.model.Job
    public boolean isBuildable() {
        return super.isBuildable();
    }

    public boolean isConfigurable() {
        return true;
    }

    public boolean blockBuildWhenDownstreamBuilding() {
        return this.blockBuildWhenDownstreamBuilding;
    }

    public void setBlockBuildWhenDownstreamBuilding(boolean b) throws IOException {
        this.blockBuildWhenDownstreamBuilding = b;
        save();
    }

    public boolean blockBuildWhenUpstreamBuilding() {
        return this.blockBuildWhenUpstreamBuilding;
    }

    public void setBlockBuildWhenUpstreamBuilding(boolean b) throws IOException {
        this.blockBuildWhenUpstreamBuilding = b;
        save();
    }

    @Exported
    public boolean isDisabled() {
        return this.disabled;
    }

    @Restricted({DoNotUse.class})
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public FormValidation doCheckRetryCount(@QueryParameter String value) throws IOException, ServletException {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.ok();
        }
        if (!value.matches("[0-9]*")) {
            return FormValidation.error("Invalid retry count");
        }
        return FormValidation.ok();
    }

    public boolean supportsMakeDisabled() {
        return this instanceof TopLevelItem;
    }

    public void disable() throws IOException {
        makeDisabled(true);
    }

    public void enable() throws IOException {
        makeDisabled(false);
    }

    @Override // hudson.model.Job
    public BallColor getIconColor() {
        if (isDisabled()) {
            return isBuilding() ? BallColor.DISABLED_ANIME : BallColor.DISABLED;
        }
        return super.getIconColor();
    }

    protected void updateTransientActions() {
        this.transientActions = createTransientActions();
    }

    protected List<Action> createTransientActions() {
        Vector<Action> ta = new Vector<>();
        for (JobProperty<? super P> p : Util.fixNull(this.properties)) {
            ta.addAll(p.getJobActions(this));
        }
        Iterator it = TransientProjectActionFactory.all().iterator();
        while (it.hasNext()) {
            TransientProjectActionFactory tpaf = (TransientProjectActionFactory) it.next();
            try {
                ta.addAll(Util.fixNull(tpaf.createFor(this)));
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Could not load actions from " + String.valueOf(tpaf) + " for " + String.valueOf(this), (Throwable) e);
            }
        }
        return ta;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // hudson.model.Job
    public void addProperty(JobProperty<? super P> jobProp) throws IOException {
        super.addProperty(jobProp);
        updateTransientActions();
    }

    public List<ProminentProjectAction> getProminentActions() {
        return getActions(ProminentProjectAction.class);
    }

    @Override // hudson.model.Job
    @POST
    public void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        super.doConfigSubmit(req, rsp);
        updateTransientActions();
        Jenkins.get().getQueue().m44scheduleMaintenance();
        Jenkins.get().rebuildDependencyGraphAsync();
    }

    public boolean scheduleBuild(int quietPeriod, Cause c, Action... actions) {
        return m16scheduleBuild2(quietPeriod, c, actions) != null;
    }

    @WithBridgeMethods({Future.class})
    /* renamed from: scheduleBuild2, reason: merged with bridge method [inline-methods] */
    public QueueTaskFuture<R> m16scheduleBuild2(int quietPeriod, Cause c, Action... actions) {
        return scheduleBuild2(quietPeriod, c, (Collection<? extends Action>) Arrays.asList(actions));
    }

    @WithBridgeMethods({Future.class})
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c, Collection<? extends Action> actions) {
        List<Action> queueActions = new ArrayList<>(actions);
        if (c != null) {
            queueActions.add(new CauseAction(c));
        }
        return scheduleBuild2(quietPeriod, (Action[]) queueActions.toArray(new Action[0]));
    }

    @WithBridgeMethods({Future.class})
    /* renamed from: scheduleBuild2, reason: merged with bridge method [inline-methods] */
    public QueueTaskFuture<R> m18scheduleBuild2(int quietPeriod) {
        return m19scheduleBuild2(quietPeriod, (Cause) new Cause.LegacyCodeCause());
    }

    @WithBridgeMethods({Future.class})
    /* renamed from: scheduleBuild2, reason: merged with bridge method [inline-methods] */
    public QueueTaskFuture<R> m19scheduleBuild2(int quietPeriod, Cause c) {
        return m16scheduleBuild2(quietPeriod, c, new Action[0]);
    }

    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Action... actions) {
        return super.scheduleBuild2(quietPeriod, actions);
    }

    public boolean schedulePolling() {
        SCMTrigger scmt;
        if (isDisabled() || (scmt = (SCMTrigger) getTrigger(SCMTrigger.class)) == null) {
            return false;
        }
        scmt.run();
        return true;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // hudson.model.Job
    public boolean isInQueue() {
        return Jenkins.get().getQueue().contains(this);
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // hudson.model.Job
    public Queue.Item getQueueItem() {
        return Jenkins.get().getQueue().getItem((Queue.Task) this);
    }

    public JDK getJDK() {
        return Jenkins.get().getJDK(this.jdk);
    }

    public void setJDK(JDK jdk) throws IOException {
        this.jdk = jdk.getName();
        save();
    }

    public BuildAuthorizationToken getAuthToken() {
        return this.authToken;
    }

    @Override // hudson.model.Job
    /* renamed from: _getRuns, reason: merged with bridge method [inline-methods] */
    public RunMap<R> mo7_getRuns() {
        return this.buildMixIn._getRuns();
    }

    @Override // hudson.model.Job
    public void removeRun(R run) {
        this.buildMixIn.removeRun(run);
    }

    @Override // hudson.model.Job
    /* renamed from: getBuild, reason: merged with bridge method [inline-methods] */
    public R mo11getBuild(String id) {
        return this.buildMixIn.getBuild(id);
    }

    @Override // hudson.model.Job
    /* renamed from: getBuildByNumber, reason: merged with bridge method [inline-methods] */
    public R mo10getBuildByNumber(int n) {
        return this.buildMixIn.getBuildByNumber(n);
    }

    @Override // hudson.model.Job
    /* renamed from: getFirstBuild, reason: merged with bridge method [inline-methods] */
    public R mo5getFirstBuild() {
        return this.buildMixIn.getFirstBuild();
    }

    @Override // hudson.model.Job
    @CheckForNull
    /* renamed from: getLastBuild, reason: merged with bridge method [inline-methods] */
    public R mo6getLastBuild() {
        return this.buildMixIn.getLastBuild();
    }

    @Override // hudson.model.Job
    /* renamed from: getNearestBuild, reason: merged with bridge method [inline-methods] */
    public R mo9getNearestBuild(int n) {
        return this.buildMixIn.getNearestBuild(n);
    }

    @Override // hudson.model.Job
    /* renamed from: getNearestOldBuild, reason: merged with bridge method [inline-methods] */
    public R mo8getNearestOldBuild(int n) {
        return this.buildMixIn.getNearestOldBuild(n);
    }

    @Override // hudson.model.Job
    protected List<R> getEstimatedDurationCandidates() {
        return this.buildMixIn.getEstimatedDurationCandidates();
    }

    protected synchronized R newBuild() throws IOException {
        return this.buildMixIn.newBuild();
    }

    protected R loadBuild(File dir) throws IOException {
        return this.buildMixIn.loadBuild(dir);
    }

    @NonNull
    public List<Action> getActions() {
        List<Action> actions = new Vector<>(super.getActions());
        actions.addAll(this.transientActions);
        return Collections.unmodifiableList(actions);
    }

    public Node getLastBuiltOn() {
        AbstractBuild b = mo6getLastBuild();
        if (b == null) {
            return null;
        }
        return b.getBuiltOn();
    }

    public Object getSameNodeConstraint() {
        return this;
    }

    @Deprecated
    /* loaded from: AbstractProject$BecauseOfBuildInProgress.class */
    public static class BecauseOfBuildInProgress extends BlockedBecauseOfBuildInProgress {
        public BecauseOfBuildInProgress(@NonNull AbstractBuild<?, ?> build) {
            super(build);
        }
    }

    /* loaded from: AbstractProject$BecauseOfDownstreamBuildInProgress.class */
    public static class BecauseOfDownstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?, ?> up;

        public BecauseOfDownstreamBuildInProgress(AbstractProject<?, ?> up) {
            this.up = up;
        }

        public String getShortDescription() {
            return Messages.AbstractProject_DownstreamBuildInProgress(this.up.getName());
        }
    }

    /* loaded from: AbstractProject$BecauseOfUpstreamBuildInProgress.class */
    public static class BecauseOfUpstreamBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?, ?> up;

        public BecauseOfUpstreamBuildInProgress(AbstractProject<?, ?> up) {
            this.up = up;
        }

        public String getShortDescription() {
            return Messages.AbstractProject_UpstreamBuildInProgress(this.up.getName());
        }
    }

    public CauseOfBlockage getCauseOfBlockage() {
        AbstractProject<?, ?> bup;
        AbstractProject<?, ?> bup2;
        if (!isConcurrentBuild() && isLogUpdated()) {
            R lastBuild = mo6getLastBuild();
            if (lastBuild != null) {
                return new BlockedBecauseOfBuildInProgress(lastBuild);
            }
            LOGGER.log(Level.FINE, "The last build has been deleted during the non-concurrent cause creation. The build is not blocked anymore");
        }
        if (blockBuildWhenDownstreamBuilding() && (bup2 = getBuildingDownstream()) != null) {
            return new BecauseOfDownstreamBuildInProgress(bup2);
        }
        if (blockBuildWhenUpstreamBuilding() && (bup = getBuildingUpstream()) != null) {
            return new BecauseOfUpstreamBuildInProgress(bup);
        }
        return null;
    }

    public AbstractProject getBuildingDownstream() {
        Set<Queue.Task> tasks = Jenkins.get().getQueue().getUnblockedTasks();
        for (Queue.BlockedItem item : Jenkins.get().getQueue().getBlockedItems()) {
            if (!item.isCauseOfBlockageNull() && !(item.getCauseOfBlockage() instanceof BecauseOfUpstreamBuildInProgress) && !(item.getCauseOfBlockage() instanceof BecauseOfDownstreamBuildInProgress)) {
                tasks.add(item.task);
            }
        }
        for (AbstractProject tup : getTransitiveDownstreamProjects()) {
            if (tup != this && (tup.isBuilding() || tasks.contains(tup))) {
                return tup;
            }
        }
        return null;
    }

    public AbstractProject getBuildingUpstream() {
        Set<Queue.Task> tasks = Jenkins.get().getQueue().getUnblockedTasks();
        for (Queue.BlockedItem item : Jenkins.get().getQueue().getBlockedItems()) {
            if (!item.isCauseOfBlockageNull() && !(item.getCauseOfBlockage() instanceof BecauseOfUpstreamBuildInProgress) && !(item.getCauseOfBlockage() instanceof BecauseOfDownstreamBuildInProgress)) {
                tasks.add(item.task);
            }
        }
        for (AbstractProject tup : getTransitiveUpstreamProjects()) {
            if (tup != this && (tup.isBuilding() || tasks.contains(tup))) {
                return tup;
            }
        }
        return null;
    }

    /* renamed from: getSubTasks, reason: merged with bridge method [inline-methods] */
    public List<SubTask> m12getSubTasks() {
        List<SubTask> r = new ArrayList<>();
        r.add(this);
        Iterator it = SubTaskContributor.all().iterator();
        while (it.hasNext()) {
            SubTaskContributor euc = (SubTaskContributor) it.next();
            r.addAll(euc.forProject(this));
        }
        Iterator it2 = this.properties.iterator();
        while (it2.hasNext()) {
            JobProperty<? super P> p = (JobProperty) it2.next();
            r.addAll(p.getSubTasks());
        }
        return r;
    }

    @CheckForNull
    /* renamed from: createExecutable, reason: merged with bridge method [inline-methods] and merged with bridge method [inline-methods] */
    public R m14createExecutable() throws IOException {
        if (isDisabled()) {
            return null;
        }
        return newBuild();
    }

    public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    @Deprecated
    public Resource getWorkspaceResource() {
        return new Resource(getFullDisplayName() + " workspace");
    }

    public ResourceList getResourceList() {
        Set<ResourceActivity> resourceActivities = getResourceActivities();
        List<ResourceList> resourceLists = new ArrayList<>(1 + resourceActivities.size());
        for (ResourceActivity activity : resourceActivities) {
            if (activity != this && activity != null) {
                resourceLists.add(activity.getResourceList());
            }
        }
        return ResourceList.union(resourceLists);
    }

    protected Set<ResourceActivity> getResourceActivities() {
        return Collections.emptySet();
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        SCM scm = getScm();
        if (scm == null) {
            return true;
        }
        FilePath workspace = build.getWorkspace();
        if (workspace != null) {
            workspace.mkdirs();
            boolean r = scm.checkout(build, launcher, workspace, listener, changelogFile);
            if (r) {
                calcPollingBaseline(build, launcher, listener);
            }
            return r;
        }
        throw new AbortException("Cannot checkout SCM, workspace is not defined");
    }

    private void calcPollingBaseline(AbstractBuild build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        SCMRevisionState baseline = build.getAction(SCMRevisionState.class);
        if (baseline == null) {
            try {
                baseline = getScm().calcRevisionsFromBuild(build, launcher, listener);
            } catch (AbstractMethodError e) {
                baseline = SCMRevisionState.NONE;
            }
            if (baseline != null) {
                build.addAction(baseline);
            }
        }
        this.pollingBaseline = baseline;
    }

    @Deprecated
    public boolean pollSCMChanges(TaskListener listener) {
        return poll(listener).hasChanges();
    }

    public PollingResult poll(TaskListener listener) {
        SCM scm = getScm();
        if (scm == null) {
            listener.getLogger().println(Messages.AbstractProject_NoSCM());
            return PollingResult.NO_CHANGES;
        }
        if (!isBuildable()) {
            listener.getLogger().println(Messages.AbstractProject_Disabled());
            return PollingResult.NO_CHANGES;
        }
        SCMDecisionHandler veto = SCMDecisionHandler.firstShouldPollVeto(this);
        if (veto != null) {
            listener.getLogger().println(Messages.AbstractProject_PollingVetoed(veto));
            return PollingResult.NO_CHANGES;
        }
        R lb = mo6getLastBuild();
        if (lb == null) {
            listener.getLogger().println(Messages.AbstractProject_NoBuilds());
            return isInQueue() ? PollingResult.NO_CHANGES : PollingResult.BUILD_NOW;
        }
        if (this.pollingBaseline == null) {
            AbstractBuild abstractBuild = (AbstractBuild) getLastSuccessfulBuild();
            AbstractBuild abstractBuild2 = lb;
            while (true) {
                AbstractBuild abstractBuild3 = abstractBuild2;
                if (abstractBuild3 != null) {
                    SCMRevisionState s = abstractBuild3.getAction(SCMRevisionState.class);
                    if (s != null) {
                        this.pollingBaseline = s;
                        break;
                    }
                    if (abstractBuild3 != abstractBuild) {
                        abstractBuild2 = abstractBuild3.getPreviousBuild();
                    }
                }
            }
        }
        try {
            SCMPollListener.fireBeforePolling(this, listener);
            PollingResult r = _poll(listener, scm);
            SCMPollListener.firePollingSuccess(this, listener, r);
            return r;
        } catch (IOException e) {
            Functions.printStackTrace(e, listener.fatalError(e.getMessage()));
            SCMPollListener.firePollingFailed(this, listener, e);
            return PollingResult.NO_CHANGES;
        } catch (AbortException e2) {
            listener.getLogger().println(e2.getMessage());
            listener.fatalError(Messages.AbstractProject_Aborted());
            LOGGER.log(Level.FINE, "Polling " + String.valueOf(this) + " aborted", e2);
            SCMPollListener.firePollingFailed(this, listener, e2);
            return PollingResult.NO_CHANGES;
        } catch (Error | RuntimeException e3) {
            SCMPollListener.firePollingFailed(this, listener, e3);
            throw e3;
        } catch (InterruptedException e4) {
            Functions.printStackTrace(e4, listener.fatalError(Messages.AbstractProject_PollingABorted()));
            SCMPollListener.firePollingFailed(this, listener, e4);
            return PollingResult.NO_CHANGES;
        }
    }

    private PollingResult _poll(TaskListener listener, SCM scm) throws IOException, InterruptedException {
        String AbstractProject_NoWorkspace;
        if (scm.requiresWorkspaceForPolling()) {
            R b = getSomeBuildWithExistingWorkspace();
            if (b == null) {
                b = mo6getLastBuild();
            }
            FilePath ws = b.getWorkspace();
            WorkspaceOfflineReason workspaceOfflineReason = workspaceOffline(b);
            if (workspaceOfflineReason != null) {
                Iterator it = ExtensionList.lookup(WorkspaceBrowser.class).iterator();
                while (it.hasNext()) {
                    WorkspaceBrowser browser = (WorkspaceBrowser) it.next();
                    ws = browser.getWorkspace(this);
                    if (ws != null) {
                        return pollWithWorkspace(listener, scm, b, ws, browser.getWorkspaceList());
                    }
                }
                long running = ((Uptime) Jenkins.get().getInjector().getInstance(Uptime.class)).getUptime();
                long remaining = TimeUnit.MINUTES.toMillis(10L) - running;
                if (remaining > 0 && !Functions.getIsUnitTest()) {
                    listener.getLogger().print(Messages.AbstractProject_AwaitingWorkspaceToComeOnline(Long.valueOf(remaining / 1000)));
                    listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                    return PollingResult.NO_CHANGES;
                }
                if (workspaceOfflineReason.equals(WorkspaceOfflineReason.all_suitable_nodes_are_offline)) {
                    listener.getLogger().print(Messages.AbstractProject_AwaitingWorkspaceToComeOnline(Long.valueOf(running / 1000)));
                    listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                    return PollingResult.NO_CHANGES;
                }
                Label label = getAssignedLabel();
                if (label != null && label.isSelfLabel()) {
                    listener.getLogger().print(Messages.AbstractProject_NoWorkspace());
                    listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                    return PollingResult.NO_CHANGES;
                }
                PrintStream logger = listener.getLogger();
                if (ws == null) {
                    AbstractProject_NoWorkspace = Messages.AbstractProject_WorkspaceOffline();
                } else {
                    AbstractProject_NoWorkspace = Messages.AbstractProject_NoWorkspace();
                }
                logger.println(AbstractProject_NoWorkspace);
                if (isInQueue()) {
                    listener.getLogger().println(Messages.AbstractProject_AwaitingBuildForWorkspace());
                    return PollingResult.NO_CHANGES;
                }
                listener.getLogger().print(Messages.AbstractProject_NewBuildForWorkspace());
                listener.getLogger().println(" (" + workspaceOfflineReason.name() + ")");
                return PollingResult.BUILD_NOW;
            }
            WorkspaceList l = b.getBuiltOn().toComputer().getWorkspaceList();
            return pollWithWorkspace(listener, scm, b, ws, l);
        }
        LOGGER.fine("Polling SCM changes of " + getName());
        if (this.pollingBaseline == null) {
            calcPollingBaseline(mo6getLastBuild(), null, listener);
        }
        PollingResult r = scm.poll(this, (Launcher) null, (FilePath) null, listener, this.pollingBaseline);
        this.pollingBaseline = r.remote;
        return r;
    }

    private PollingResult pollWithWorkspace(TaskListener listener, SCM scm, R lb, @NonNull FilePath ws, WorkspaceList l) throws InterruptedException, IOException {
        String name;
        Node node = lb.getBuiltOn();
        Launcher launcher = ws.createLauncher(listener).decorateByEnv(getEnvironment(node, listener));
        WorkspaceList.Lease lease = l.acquire(ws, !this.concurrentBuild);
        if (node != null) {
            try {
                name = node.m40getSelfLabel().getName();
            } catch (Throwable th) {
                lease.release();
                throw th;
            }
        } else {
            name = "[node_unavailable]";
        }
        String nodeName = name;
        listener.getLogger().println("Polling SCM changes on " + nodeName);
        LOGGER.fine("Polling SCM changes of " + getName());
        if (this.pollingBaseline == null) {
            calcPollingBaseline(lb, launcher, listener);
        }
        PollingResult r = scm.poll(this, launcher, ws, listener, this.pollingBaseline);
        this.pollingBaseline = r.remote;
        lease.release();
        return r;
    }

    private boolean isAllSuitableNodesOffline(R build) {
        Label label = getAssignedLabel();
        if (label != null) {
            if (label.getNodes().isEmpty()) {
                return false;
            }
            return label.isOffline();
        }
        if (this.canRoam) {
            for (Node n : Jenkins.get().getNodes()) {
                Computer c = n.toComputer();
                if (c != null && c.isOnline() && c.isAcceptingTasks() && n.getMode() == Node.Mode.NORMAL) {
                    return false;
                }
            }
            return Jenkins.get().getMode() == Node.Mode.EXCLUSIVE;
        }
        return true;
    }

    private WorkspaceOfflineReason workspaceOffline(R build) throws IOException, InterruptedException {
        FilePath ws = build.getWorkspace();
        Label label = getAssignedLabel();
        if (isAllSuitableNodesOffline(build)) {
            return (label == null ? Jenkins.get().clouds : label.getClouds()).isEmpty() ? WorkspaceOfflineReason.all_suitable_nodes_are_offline : WorkspaceOfflineReason.use_ondemand_slave;
        }
        if (ws == null || !ws.exists()) {
            return WorkspaceOfflineReason.nonexisting_workspace;
        }
        Node builtOn = build.getBuiltOn();
        if (builtOn == null) {
            return WorkspaceOfflineReason.builton_node_gone;
        }
        if (builtOn.toComputer() == null) {
            return WorkspaceOfflineReason.builton_node_no_executors;
        }
        return null;
    }

    public boolean hasParticipant(User user) {
        AbstractBuild mo6getLastBuild = mo6getLastBuild();
        while (true) {
            AbstractBuild abstractBuild = mo6getLastBuild;
            if (abstractBuild != null) {
                if (!abstractBuild.hasParticipant(user)) {
                    mo6getLastBuild = abstractBuild.getPreviousBuild();
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Exported
    public SCM getScm() {
        return this.scm;
    }

    public void setScm(SCM scm) throws IOException {
        this.scm = scm;
        save();
    }

    public void addTrigger(Trigger<?> trigger) throws IOException {
        addToList(trigger, m15triggers());
    }

    public void removeTrigger(TriggerDescriptor trigger) throws IOException {
        removeFromList(trigger, m15triggers());
    }

    protected final synchronized <T extends Describable<T>> void addToList(T item, List<T> collection) throws IOException {
        removeFromList(item.getDescriptor(), collection);
        collection.add(item);
        save();
        updateTransientActions();
    }

    protected final synchronized <T extends Describable<T>> void removeFromList(Descriptor<T> item, List<T> collection) throws IOException {
        Iterator<T> iCollection = collection.iterator();
        while (iCollection.hasNext()) {
            T next = iCollection.next();
            if (next.getDescriptor() == item) {
                iCollection.remove();
                save();
                updateTransientActions();
                return;
            }
        }
    }

    public Map<TriggerDescriptor, Trigger<?>> getTriggers() {
        return m15triggers().toMap();
    }

    public <T extends Trigger> T getTrigger(Class<T> clazz) {
        Iterator it = m15triggers().iterator();
        while (it.hasNext()) {
            Trigger p = (Trigger) it.next();
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    public final List<AbstractProject> getDownstreamProjects() {
        return Jenkins.get().getDependencyGraph().getDownstream(this);
    }

    @Exported(name = "downstreamProjects")
    @Restricted({DoNotUse.class})
    public List<AbstractProject> getDownstreamProjectsForApi() {
        List<AbstractProject> r = new ArrayList<>();
        for (AbstractProject p : getDownstreamProjects()) {
            if (p.hasPermission(Item.READ)) {
                r.add(p);
            }
        }
        return r;
    }

    public final List<AbstractProject> getUpstreamProjects() {
        return Jenkins.get().getDependencyGraph().getUpstream(this);
    }

    @Exported(name = "upstreamProjects")
    @Restricted({DoNotUse.class})
    public List<AbstractProject> getUpstreamProjectsForApi() {
        List<AbstractProject> r = new ArrayList<>();
        for (AbstractProject p : getUpstreamProjects()) {
            if (p.hasPermission(Item.READ)) {
                r.add(p);
            }
        }
        return r;
    }

    public final List<AbstractProject> getBuildTriggerUpstreamProjects() {
        ArrayList<AbstractProject> result = new ArrayList<>();
        for (AbstractProject<?, ?> ap : getUpstreamProjects()) {
            BuildTrigger buildTrigger = ap.getPublishersList().get(BuildTrigger.class);
            if (buildTrigger != null && buildTrigger.getChildJobs(ap).contains(this)) {
                result.add(ap);
            }
        }
        return result;
    }

    public final Set<AbstractProject> getTransitiveUpstreamProjects() {
        return Jenkins.get().getDependencyGraph().getTransitiveUpstream(this);
    }

    public final Set<AbstractProject> getTransitiveDownstreamProjects() {
        return Jenkins.get().getDependencyGraph().getTransitiveDownstream(this);
    }

    public SortedMap<Integer, Fingerprint.RangeSet> getRelationship(AbstractProject that) {
        TreeMap<Integer, Fingerprint.RangeSet> r = new TreeMap<>(REVERSE_INTEGER_COMPARATOR);
        checkAndRecord(that, r, m28getBuilds());
        return r;
    }

    private void checkAndRecord(AbstractProject that, TreeMap<Integer, Fingerprint.RangeSet> r, Iterable<R> builds) {
        for (R build : builds) {
            Fingerprint.RangeSet rs = build.getDownstreamRelationship(that);
            if (rs != null && !rs.isEmpty()) {
                int n = build.getNumber();
                Fingerprint.RangeSet value = r.get(Integer.valueOf(n));
                if (value == null) {
                    r.put(Integer.valueOf(n), rs);
                } else {
                    value.add(rs);
                }
            }
        }
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        m15triggers().buildDependencyGraph(this, graph);
    }

    @Override // hudson.model.Job
    protected SearchIndexBuilder makeSearchIndex() {
        return getParameterizedJobMixIn().extendSearchIndex(super.makeSearchIndex());
    }

    @Deprecated
    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doBuild(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), TimeDuration.fromString(req.getParameter("delay")));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    @Deprecated
    public int getDelay(StaplerRequest req) throws javax.servlet.ServletException {
        String delay = req.getParameter("delay");
        if (delay == null) {
            return getQuietPeriod();
        }
        try {
            if (delay.endsWith("sec")) {
                delay = delay.substring(0, delay.length() - 3);
            }
            if (delay.endsWith("secs")) {
                delay = delay.substring(0, delay.length() - 4);
            }
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            throw new javax.servlet.ServletException("Invalid delay parameter value: " + delay, e);
        }
    }

    @Deprecated
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doBuildWithParameters(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), TimeDuration.fromString(req.getParameter("delay")));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    public void doPolling(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        BuildAuthorizationToken.checkPermission(this, this.authToken, req, rsp);
        schedulePolling();
        rsp.sendRedirect(".");
    }

    @Override // hudson.model.Job
    protected void submit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        if (Util.isOverridden(AbstractProject.class, getClass(), "submit", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            super.submit(req, rsp);
            submitImpl(req, rsp);
        }
    }

    @Override // hudson.model.Job
    @Deprecated
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        try {
            submitImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void submitImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        JSONObject json = req.getSubmittedForm();
        makeDisabled(!json.optBoolean("enable"));
        this.jdk = json.optString("jdk", (String) null);
        if (json.optBoolean("hasCustomQuietPeriod", json.has("quiet_period"))) {
            this.quietPeriod = Integer.valueOf(json.optInt("quiet_period"));
        } else {
            this.quietPeriod = null;
        }
        if (json.optBoolean("hasCustomScmCheckoutRetryCount", json.has("scmCheckoutRetryCount"))) {
            this.scmCheckoutRetryCount = Integer.valueOf(json.optInt("scmCheckoutRetryCount"));
        } else {
            this.scmCheckoutRetryCount = null;
        }
        this.blockBuildWhenDownstreamBuilding = json.optBoolean("blockBuildWhenDownstreamBuilding");
        this.blockBuildWhenUpstreamBuilding = json.optBoolean("blockBuildWhenUpstreamBuilding");
        if (req.hasParameter("customWorkspace.directory")) {
            LOGGER.log(Level.WARNING, "label assignment is using legacy 'customWorkspace.directory'");
            this.customWorkspace = Util.fixEmptyAndTrim(req.getParameter("customWorkspace.directory"));
        } else if (json.optBoolean("hasCustomWorkspace", json.has("customWorkspace"))) {
            this.customWorkspace = Util.fixEmptyAndTrim(json.optString("customWorkspace"));
        } else {
            this.customWorkspace = null;
        }
        if (json.has("scmCheckoutStrategy")) {
            this.scmCheckoutStrategy = (SCMCheckoutStrategy) req.bindJSON(SCMCheckoutStrategy.class, json.getJSONObject("scmCheckoutStrategy"));
        } else {
            this.scmCheckoutStrategy = null;
        }
        if (json.optBoolean("hasSlaveAffinity", json.has("label"))) {
            this.assignedNode = Util.fixEmptyAndTrim(json.optString("label"));
        } else if (req.hasParameter("_.assignedLabelString")) {
            LOGGER.log(Level.WARNING, "label assignment is using legacy '_.assignedLabelString'");
            this.assignedNode = Util.fixEmptyAndTrim(req.getParameter("_.assignedLabelString"));
        } else {
            this.assignedNode = null;
        }
        this.canRoam = this.assignedNode == null;
        this.keepDependencies = json.has("keepDependencies");
        this.concurrentBuild = json.optBoolean("concurrentBuild");
        this.authToken = BuildAuthorizationToken.create(req);
        setScm(SCMS.parseSCM(req, this));
        Iterator it = m15triggers().iterator();
        while (it.hasNext()) {
            Trigger t = (Trigger) it.next();
            t.stop();
        }
        this.triggers.replaceBy(buildDescribable(req, Trigger.for_(this)));
        Iterator it2 = m15triggers().iterator();
        while (it2.hasNext()) {
            Trigger t2 = (Trigger) it2.next();
            t2.start(this, true);
        }
    }

    @Deprecated
    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req, List<? extends Descriptor<T>> descriptors, String prefix) throws Descriptor.FormException, javax.servlet.ServletException {
        try {
            return buildDescribable(StaplerRequest.toStaplerRequest2(req), descriptors);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    protected final <T extends Describable<T>> List<T> buildDescribable(StaplerRequest2 req, List<? extends Descriptor<T>> descriptors) throws Descriptor.FormException, ServletException {
        JSONObject data = req.getSubmittedForm();
        Vector vector = new Vector();
        for (Descriptor<T> d : descriptors) {
            String safeName = d.getJsonSafeClassName();
            if (req.getParameter(safeName) != null) {
                vector.add(d.newInstance(req, data.getJSONObject(safeName)));
            }
        }
        return vector;
    }

    public DirectoryBrowserSupport doWs(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, InterruptedException {
        String title;
        checkPermission(Item.WORKSPACE);
        FilePath ws = getSomeWorkspace();
        if (ws == null || !ws.exists()) {
            req.getView(this, "noWorkspace.jelly").forward(req, rsp);
            return null;
        }
        Computer c = ws.toComputer();
        if (c == null) {
            title = Messages.AbstractProject_WorkspaceTitle(getDisplayName());
        } else {
            title = Messages.AbstractProject_WorkspaceTitleOnComputer(getDisplayName(), c.getDisplayName());
        }
        return new DirectoryBrowserSupport(this, ws, title, "folder.png", true);
    }

    @RequirePOST
    public HttpResponse doDoWipeOutWorkspace() throws IOException, InterruptedException {
        checkPermission(Functions.isWipeOutPermissionEnabled() ? WIPEOUT : BUILD);
        R b = getSomeBuildWithWorkspace();
        FilePath ws = b != null ? b.getWorkspace() : null;
        if (ws != null && getScm().processWorkspaceBeforeDeletion(this, ws, b.getBuiltOn())) {
            ws.deleteRecursive();
            Iterator it = WorkspaceListener.all().iterator();
            while (it.hasNext()) {
                WorkspaceListener wl = (WorkspaceListener) it.next();
                wl.afterDelete(this);
            }
            return new HttpRedirect(".");
        }
        return new ForwardToView(this, "wipeOutWorkspaceBlocked.jelly");
    }

    /* loaded from: AbstractProject$AbstractProjectDescriptor.class */
    public static abstract class AbstractProjectDescriptor extends TopLevelItemDescriptor {
        public boolean isApplicable(Descriptor descriptor) {
            return true;
        }

        @Restricted({DoNotUse.class})
        public FormValidation doCheckAssignedLabelString(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String value) {
            AbstractProject.LOGGER.log(Level.WARNING, "checking label via legacy '_.assignedLabelString'");
            return doCheckLabel(project, value);
        }

        public FormValidation doCheckLabel(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String value) {
            return LabelExpression.validate(value, project);
        }

        @NonNull
        @Deprecated
        public static FormValidation validateLabelExpression(String value, @CheckForNull AbstractProject<?, ?> project) {
            return LabelExpression.validate(value, project);
        }

        public FormValidation doCheckCustomWorkspace(@QueryParameter String customWorkspace) {
            if (Util.fixEmptyAndTrim(customWorkspace) == null) {
                return FormValidation.error(Messages.AbstractProject_CustomWorkspaceEmpty());
            }
            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteUpstreamProjects(@QueryParameter String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<TopLevelItem> jobs = Jenkins.get().getItems(j -> {
                return (j instanceof Job) && j.getFullName().startsWith(value);
            });
            for (TopLevelItem job : jobs) {
                candidates.add(job.getFullName());
            }
            return candidates;
        }

        @Restricted({DoNotUse.class})
        public AutoCompletionCandidates doAutoCompleteAssignedLabelString(@QueryParameter String value) {
            AbstractProject.LOGGER.log(Level.WARNING, "autocompleting label via legacy '_.assignedLabelString'");
            return doAutoCompleteLabel(value);
        }

        public AutoCompletionCandidates doAutoCompleteLabel(@QueryParameter String value) {
            return LabelExpression.autoComplete(value);
        }

        public List<SCMCheckoutStrategyDescriptor> getApplicableSCMCheckoutStrategyDescriptors(AbstractProject p) {
            return SCMCheckoutStrategyDescriptor._for(p);
        }
    }

    @CheckForNull
    public static AbstractProject findNearest(String name) {
        return findNearest(name, Jenkins.get());
    }

    @CheckForNull
    public static AbstractProject findNearest(String name, ItemGroup context) {
        return (AbstractProject) Items.findNearest(AbstractProject.class, name, context);
    }

    @CLIResolver
    public static AbstractProject resolveForCLI(@Argument(required = true, metaVar = "NAME", usage = "Job name") String name) throws CmdLineException {
        AbstractProject item = (AbstractProject) Jenkins.get().getItemByFullName(name, AbstractProject.class);
        if (item == null) {
            AbstractProject project = findNearest(name);
            throw new CmdLineException((CmdLineParser) null, project == null ? Messages.AbstractItem_NoSuchJobExistsWithoutSuggestion(name) : Messages.AbstractItem_NoSuchJobExists(name, project.getFullName()));
        }
        return item;
    }

    public String getCustomWorkspace() {
        return this.customWorkspace;
    }

    public void setCustomWorkspace(String customWorkspace) throws IOException {
        this.customWorkspace = Util.fixEmptyAndTrim(customWorkspace);
        save();
    }

    @Deprecated
    /* loaded from: AbstractProject$LabelValidator.class */
    public static abstract class LabelValidator implements ExtensionPoint {
        @NonNull
        public abstract FormValidation check(@NonNull AbstractProject<?, ?> project, @NonNull Label label);

        @NonNull
        public FormValidation checkItem(@NonNull Item item, @NonNull Label label) {
            if (item instanceof AbstractProject) {
                return check((AbstractProject) item, label);
            }
            return FormValidation.ok();
        }
    }
}
