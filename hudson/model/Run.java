package hudson.model;

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FeedAdapter;
import hudson.Functions;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleLogFilter;
import hudson.console.ConsoleNote;
import hudson.console.ModelHyperlinkNote;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Maven;
import hudson.util.FormApply;
import hudson.util.LogTaskListener;
import hudson.util.XStream2;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import jenkins.console.ConsoleUrlProvider;
import jenkins.console.WithConsoleUrl;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.HistoricalBuild;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.RunAction2;
import jenkins.model.StandardArtifactManager;
import jenkins.model.Tab;
import jenkins.model.details.CauseDetail;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.model.details.DurationDetail;
import jenkins.model.details.KeptForeverDetail;
import jenkins.model.details.TimestampDetail;
import jenkins.model.lazy.BuildReference;
import jenkins.security.MasterToSlaveCallable;
import jenkins.security.XStreamNotDeserializable;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import jenkins.util.VirtualFile;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@ExportedBean
/* loaded from: Run.class */
public abstract class Run<JobT extends Job<JobT, RunT>, RunT extends Run<JobT, RunT>> extends Actionable implements ExtensionPoint, Comparable<RunT>, AccessControlled, PersistenceRoot, DescriptorByNameOwner, OnMaster, StaplerProxy, HistoricalBuild, WithConsoleUrl {
    public static final long QUEUE_ID_UNKNOWN = -1;

    @XStreamNotDeserializable
    @NonNull
    protected final transient JobT project;

    @XStreamNotDeserializable
    public transient int number;
    private long queueId;
    volatile transient RunT previousBuildInProgress;

    @CheckForNull
    private String id;
    protected long timestamp;
    private long startTime;
    protected volatile Result result;

    @CheckForNull
    protected volatile String description;
    private volatile String displayName;

    @XStreamNotDeserializable
    private volatile transient State state;
    protected long duration;
    protected String charset;
    private boolean keepLog;

    @XStreamNotDeserializable
    private volatile transient Run<JobT, RunT>.RunExecution runner;

    @CheckForNull
    private ArtifactManager artifactManager;

    @XStreamNotDeserializable
    private transient boolean isPendingDelete;
    public static final int LIST_CUTOFF = Integer.parseInt(SystemProperties.getString("hudson.model.Run.ArtifactList.listCutoff", "20"));
    public static final XStream XSTREAM = new XStream2();
    public static final XStream2 XSTREAM2 = (XStream2) XSTREAM;
    private static final Logger LOGGER;
    public static final Comparator<Run> ORDER_BY_DATE;
    public static final FeedAdapter<Run> FEED_ADAPTER;
    public static final FeedAdapter<Run> FEED_ADAPTER_LATEST;
    public static final PermissionGroup PERMISSIONS;
    public static final Permission DELETE;
    public static final Permission UPDATE;
    public static final Permission ARTIFACTS;

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean SKIP_PERMISSION_CHECK;

    /* loaded from: Run$RunnerAbortedException.class */
    public static final class RunnerAbortedException extends RuntimeException {
        private static final long serialVersionUID = 1;
    }

    /* loaded from: Run$State.class */
    private enum State {
        NOT_STARTED,
        BUILDING,
        POST_PRODUCTION,
        COMPLETED
    }

    /* loaded from: Run$StatusSummarizer.class */
    public static abstract class StatusSummarizer implements ExtensionPoint {
        @CheckForNull
        public abstract Summary summarize(@NonNull Run<?, ?> run, @NonNull ResultTrend trend);
    }

    protected Run(@NonNull JobT job) throws IOException {
        this(job, System.currentTimeMillis());
        this.number = this.project.assignBuildNumber();
        LOGGER.log(Level.FINER, "new {0} @{1}", new Object[]{this, Integer.valueOf(hashCode())});
    }

    protected Run(@NonNull JobT job, @NonNull Calendar timestamp) {
        this(job, timestamp.getTimeInMillis());
    }

    protected Run(@NonNull JobT job, long timestamp) {
        this.queueId = -1L;
        this.project = job;
        this.timestamp = timestamp;
        this.state = State.NOT_STARTED;
    }

    protected Run(@NonNull JobT project, @NonNull File buildDir) throws IOException {
        this.queueId = -1L;
        this.project = project;
        this.previousBuildInProgress = _this();
        this.number = Integer.parseInt(buildDir.getName());
        reload();
    }

    public void reload() throws IOException {
        this.state = State.COMPLETED;
        this.result = Result.ABORTED;
        getDataFile().unmarshal(this);
        if (this.state == State.COMPLETED) {
            LOGGER.log(Level.FINER, "reload {0} @{1}", new Object[]{this, Integer.valueOf(hashCode())});
        } else {
            LOGGER.log(Level.WARNING, "reload {0} @{1} with anomalous state {2}", new Object[]{this, Integer.valueOf(hashCode()), this.state});
        }
    }

    protected void onLoad() {
        for (RunAction2 runAction2 : getActions()) {
            if (runAction2 instanceof RunAction2) {
                try {
                    runAction2.onLoad(this);
                } catch (RuntimeException x) {
                    LOGGER.log(Level.WARNING, "failed to load " + String.valueOf(runAction2) + " from " + String.valueOf(getDataFile()), (Throwable) x);
                    removeAction(runAction2);
                }
            } else if (runAction2 instanceof RunAction) {
                ((RunAction) runAction2).onLoad();
            }
        }
        if (this.artifactManager != null) {
            this.artifactManager.onLoad(this);
        }
    }

    @Deprecated
    public List<Action> getTransientActions() {
        List<Action> actions = new ArrayList<>();
        Iterator it = TransientBuildActionFactory.all().iterator();
        while (it.hasNext()) {
            TransientBuildActionFactory factory = (TransientBuildActionFactory) it.next();
            for (Action created : factory.createFor(this)) {
                if (created == null) {
                    LOGGER.log(Level.WARNING, "null action added by {0}", factory);
                } else {
                    actions.add(created);
                }
            }
        }
        return Collections.unmodifiableList(actions);
    }

    public void addAction(@NonNull Action a) {
        super.addAction(a);
        if (a instanceof RunAction2) {
            ((RunAction2) a).onAttached(this);
        } else if (a instanceof RunAction) {
            ((RunAction) a).onAttached(this);
        }
    }

    @NonNull
    protected RunT _this() {
        return this;
    }

    @Override // java.lang.Comparable
    public int compareTo(@NonNull RunT that) {
        int res = this.number - that.number;
        if (res == 0) {
            return getParent().getFullName().compareTo(that.getParent().getFullName());
        }
        return res;
    }

    @Exported
    public long getQueueId() {
        return this.queueId;
    }

    @Restricted({NoExternalUse.class})
    public void setQueueId(long queueId) {
        this.queueId = queueId;
    }

    @Exported
    @CheckForNull
    public Result getResult() {
        return this.result;
    }

    public void setResult(@NonNull Result r) {
        if (this.state != State.BUILDING) {
            throw new IllegalStateException("cannot change build result while in " + String.valueOf(this.state));
        }
        if (this.result == null || r.isWorseThan(this.result)) {
            this.result = r;
            LOGGER.log(Level.FINE, String.valueOf(this) + " in " + String.valueOf(getRootDir()) + ": result is set to " + String.valueOf(r), (Throwable) (LOGGER.isLoggable(Level.FINER) ? new Exception() : null));
        }
    }

    @NonNull
    public List<BuildBadgeAction> getBadgeActions() {
        List<BuildBadgeAction> r = getActions(BuildBadgeAction.class);
        if (isKeepLog()) {
            r = new ArrayList(r);
            r.add(new KeepLogBuildBadge());
        }
        return r;
    }

    @Exported
    public boolean isBuilding() {
        return this.state.compareTo(State.POST_PRODUCTION) < 0;
    }

    @Exported
    public boolean isInProgress() {
        return this.state.equals(State.BUILDING) || this.state.equals(State.POST_PRODUCTION);
    }

    public boolean isLogUpdated() {
        return this.state.compareTo(State.COMPLETED) < 0;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Exported
    @CheckForNull
    public Executor getExecutor() {
        if (this instanceof Queue.Executable) {
            return Executor.of((Queue.Executable) this);
        }
        return null;
    }

    @CheckForNull
    public Executor getOneOffExecutor() {
        for (Computer c : Jenkins.get().getComputers()) {
            for (Executor e : c.getOneOffExecutors()) {
                if (e.getCurrentExecutable() == this) {
                    return e;
                }
            }
        }
        return null;
    }

    @NonNull
    public final Charset getCharset() {
        return this.charset == null ? Charset.defaultCharset() : Charset.forName(this.charset);
    }

    @NonNull
    public List<Cause> getCauses() {
        CauseAction a = getAction(CauseAction.class);
        return a == null ? Collections.emptyList() : Collections.unmodifiableList(a.getCauses());
    }

    @CheckForNull
    public <T extends Cause> T getCause(Class<T> type) {
        for (Cause c : getCauses()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    @Exported
    public final boolean isKeepLog() {
        return getWhyKeepLog() != null;
    }

    @CheckForNull
    public String getWhyKeepLog() {
        if (this.keepLog) {
            return Messages.Run_MarkedExplicitly();
        }
        return null;
    }

    @NonNull
    public JobT getParent() {
        return this.project;
    }

    @Exported
    @NonNull
    public Calendar getTimestamp() {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(this.timestamp);
        return c;
    }

    @NonNull
    public final Date getTime() {
        return new Date(this.timestamp);
    }

    public final long getTimeInMillis() {
        return this.timestamp;
    }

    public final long getStartTimeInMillis() {
        return this.startTime == 0 ? this.timestamp : this.startTime;
    }

    @Exported
    @CheckForNull
    public String getDescription() {
        return this.description;
    }

    @NonNull
    public String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis() - this.timestamp;
        return Util.getTimeSpanString(duration);
    }

    @NonNull
    public String getTimestampString2() {
        return Util.XS_DATETIME_FORMATTER2.format(Instant.ofEpochMilli(this.timestamp));
    }

    @NonNull
    public String getDurationString() {
        if (hasntStartedYet()) {
            return Messages.Run_NotStartedYet();
        }
        if (isBuilding()) {
            return Messages.Run_InProgressDuration(Util.getTimeSpanString(System.currentTimeMillis() - this.startTime));
        }
        return Util.getTimeSpanString(this.duration);
    }

    @Exported
    public long getDuration() {
        return this.duration;
    }

    @NonNull
    public BallColor getIconColor() {
        BallColor baseColor;
        if (!isBuilding()) {
            return getResult().color;
        }
        RunT pb = getPreviousBuild();
        if (pb == null) {
            baseColor = BallColor.NOTBUILT;
        } else {
            baseColor = pb.getIconColor();
        }
        return baseColor.anime();
    }

    public boolean hasntStartedYet() {
        return this.state == State.NOT_STARTED;
    }

    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"}, justification = "see JENKINS-45892")
    public String toString() {
        if (this.project == null) {
            return "<broken data JENKINS-45892>";
        }
        return this.project.getFullName() + " #" + this.number;
    }

    @Exported
    public String getFullDisplayName() {
        return this.project.getFullDisplayName() + " " + getDisplayName();
    }

    @Exported
    public String getDisplayName() {
        return this.displayName != null ? this.displayName : "#" + this.number;
    }

    public boolean hasCustomDisplayName() {
        return this.displayName != null;
    }

    public void setDisplayName(String value) throws IOException {
        checkPermission(UPDATE);
        this.displayName = value;
        save();
    }

    @Exported(visibility = Maven.MavenInstallation.MAVEN_30)
    public int getNumber() {
        return this.number;
    }

    @NonNull
    protected BuildReference<RunT> createReference() {
        return new BuildReference<>(getId(), _this());
    }

    protected void dropLinks() {
    }

    @CheckForNull
    public RunT getPreviousBuild() {
        return null;
    }

    @CheckForNull
    public final RunT getPreviousCompletedBuild() {
        RunT runt;
        RunT previousBuild = getPreviousBuild();
        while (true) {
            runt = previousBuild;
            if (runt == null || !runt.isBuilding()) {
                break;
            }
            previousBuild = (RunT) runt.getPreviousBuild();
        }
        return runt;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v28, types: [hudson.model.Run] */
    @CheckForNull
    public final RunT getPreviousBuildInProgress() {
        RunT answer;
        if (this.previousBuildInProgress == this) {
            return null;
        }
        List<RunT> fixUp = new ArrayList<>();
        RunT _this = _this();
        while (true) {
            RunT r = _this;
            RunT n = r.previousBuildInProgress;
            if (n == null) {
                n = r.getPreviousBuild();
                fixUp.add(r);
            }
            if (r == n || n == null) {
                break;
            }
            if (n.isBuilding()) {
                answer = n;
                break;
            }
            fixUp.add(r);
            _this = n;
        }
        answer = null;
        for (RunT f : fixUp) {
            f.previousBuildInProgress = answer == null ? f : answer;
        }
        return answer;
    }

    @CheckForNull
    public RunT getPreviousBuiltBuild() {
        RunT runt;
        RunT previousBuild = getPreviousBuild();
        while (true) {
            runt = previousBuild;
            if (runt == null || !(runt.getResult() == null || runt.getResult() == Result.NOT_BUILT)) {
                break;
            }
            previousBuild = (RunT) runt.getPreviousBuild();
        }
        return runt;
    }

    @CheckForNull
    public RunT getPreviousNotFailedBuild() {
        RunT runt;
        RunT previousBuild = getPreviousBuild();
        while (true) {
            runt = previousBuild;
            if (runt == null || runt.getResult() != Result.FAILURE) {
                break;
            }
            previousBuild = (RunT) runt.getPreviousBuild();
        }
        return runt;
    }

    @CheckForNull
    public RunT getPreviousFailedBuild() {
        RunT runt;
        RunT previousBuild = getPreviousBuild();
        while (true) {
            runt = previousBuild;
            if (runt == null || runt.getResult() == Result.FAILURE) {
                break;
            }
            previousBuild = (RunT) runt.getPreviousBuild();
        }
        return runt;
    }

    @CheckForNull
    public RunT getPreviousSuccessfulBuild() {
        RunT runt;
        RunT previousBuild = getPreviousBuild();
        while (true) {
            runt = previousBuild;
            if (runt == null || runt.getResult() == Result.SUCCESS) {
                break;
            }
            previousBuild = (RunT) runt.getPreviousBuild();
        }
        return runt;
    }

    @NonNull
    public List<RunT> getPreviousBuildsOverThreshold(int numberOfBuilds, @NonNull Result threshold) {
        RunT r = getPreviousBuild();
        if (r != null) {
            return r.getBuildsOverThreshold(numberOfBuilds, threshold);
        }
        return new ArrayList(numberOfBuilds);
    }

    @NonNull
    protected List<RunT> getBuildsOverThreshold(int numberOfBuilds, @NonNull Result threshold) {
        ArrayList arrayList = new ArrayList(numberOfBuilds);
        Run _this = _this();
        while (true) {
            Run run = _this;
            if (run == null || arrayList.size() >= numberOfBuilds) {
                break;
            }
            if (!run.isBuilding() && run.getResult() != null && run.getResult().isBetterOrEqualTo(threshold)) {
                arrayList.add(run);
            }
            _this = run.getPreviousBuild();
        }
        return arrayList;
    }

    @CheckForNull
    public RunT getNextBuild() {
        return null;
    }

    @NonNull
    public String getUrl() {
        String seed;
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null && (seed = Functions.getNearestAncestorUrl(req, this)) != null) {
            return seed.substring(req.getContextPath().length() + 1) + "/";
        }
        return this.project.getUrl() + getNumber() + "/";
    }

    public String getConsoleUrl() {
        return ConsoleUrlProvider.consoleUrlOf(this);
    }

    @Exported(visibility = Maven.MavenInstallation.MAVEN_30, name = "url")
    @NonNull
    @Deprecated
    public final String getAbsoluteUrl() {
        return this.project.getAbsoluteUrl() + getNumber() + "/";
    }

    @NonNull
    public final String getSearchUrl() {
        return getNumber() + "/";
    }

    @Exported
    @NonNull
    public String getId() {
        return this.id != null ? this.id : Integer.toString(this.number);
    }

    @NonNull
    public File getRootDir() {
        return new File(this.project.getBuildDir(), Integer.toString(this.number));
    }

    public List<ParameterValue> getParameterValues() {
        ParametersAction a = getAction(ParametersAction.class);
        return a != null ? a.getParameters() : List.of();
    }

    @NonNull
    public final ArtifactManager getArtifactManager() {
        return this.artifactManager != null ? this.artifactManager : new StandardArtifactManager(this);
    }

    @NonNull
    public final synchronized ArtifactManager pickArtifactManager() throws IOException {
        if (this.artifactManager != null) {
            return this.artifactManager;
        }
        Iterator it = ArtifactManagerConfiguration.get().getArtifactManagerFactories().iterator();
        while (it.hasNext()) {
            ArtifactManagerFactory f = (ArtifactManagerFactory) it.next();
            ArtifactManager mgr = f.managerFor(this);
            if (mgr != null) {
                this.artifactManager = mgr;
                save();
                return mgr;
            }
        }
        return new StandardArtifactManager(this);
    }

    @Restricted({DoNotUse.class})
    public final boolean hasCustomArtifactManager() {
        return this.artifactManager != null;
    }

    @Deprecated
    public File getArtifactsDir() {
        return new File(getRootDir(), "archive");
    }

    @Exported
    @NonNull
    public List<Run<JobT, RunT>.Artifact> getArtifacts() {
        return getArtifactsUpTo(Integer.MAX_VALUE);
    }

    @NonNull
    public List<Run<JobT, RunT>.Artifact> getArtifactsUpTo(int artifactsNumber) {
        SerializableArtifactList sal;
        VirtualFile root = getArtifactManager().root();
        try {
            sal = (SerializableArtifactList) root.run(new AddArtifacts(root, artifactsNumber));
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, (String) null, (Throwable) x);
            sal = new SerializableArtifactList();
        }
        Run<JobT, RunT>.ArtifactList r = new ArtifactList();
        r.updateFrom(sal);
        r.computeDisplayName();
        return r;
    }

    public boolean getHasArtifacts() {
        return !getArtifactsUpTo(1).isEmpty();
    }

    /* loaded from: Run$AddArtifacts.class */
    private static final class AddArtifacts extends MasterToSlaveCallable<SerializableArtifactList, IOException> {
        private static final long serialVersionUID = 1;
        private final VirtualFile root;
        private final int artifactsNumber;

        AddArtifacts(VirtualFile root, int artifactsNumber) {
            this.root = root;
            this.artifactsNumber = artifactsNumber;
        }

        /* renamed from: call, reason: merged with bridge method [inline-methods] */
        public SerializableArtifactList m50call() throws IOException {
            SerializableArtifactList sal = new SerializableArtifactList();
            Run.addArtifacts(this.root, "", "", sal, null, this.artifactsNumber);
            return sal;
        }
    }

    private static int addArtifacts(@NonNull VirtualFile dir, @NonNull String path, @NonNull String pathHref, @NonNull SerializableArtifactList r, @CheckForNull SerializableArtifact parent, int upTo) throws IOException {
        SerializableArtifact a;
        VirtualFile[] kids = dir.list();
        Arrays.sort(kids);
        int n = 0;
        for (VirtualFile sub : kids) {
            String child = sub.getName();
            String childPath = path + child;
            String childHref = pathHref + Util.rawEncode(child);
            String length = sub.isFile() ? String.valueOf(sub.length()) : "";
            boolean collapsed = kids.length == 1 && parent != null;
            if (collapsed) {
                a = new SerializableArtifact(parent.name + "/" + child, childPath, sub.isDirectory() ? null : childHref, length, parent.treeNodeId);
                r.tree.put(a, r.tree.remove(parent));
            } else {
                String str = sub.isDirectory() ? null : childHref;
                int i = r.idSeq + 1;
                r.idSeq = i;
                a = new SerializableArtifact(child, childPath, str, length, "n" + i);
                r.tree.put(a, parent != null ? parent.treeNodeId : null);
            }
            if (sub.isDirectory()) {
                n += addArtifacts(sub, childPath + "/", childHref + "/", r, a, upTo - n);
                if (n >= upTo) {
                    break;
                }
            } else {
                r.add(collapsed ? new SerializableArtifact(child, a.relativePath, a.href, length, a.treeNodeId) : a);
                n++;
                if (n >= upTo) {
                    break;
                }
            }
        }
        return n;
    }

    static {
        XSTREAM.alias("build", FreeStyleBuild.class);
        XSTREAM.registerConverter(Result.conv);
        LOGGER = Logger.getLogger(Run.class.getName());
        ORDER_BY_DATE = (lhs, rhs) -> {
            long lt = lhs.getTimeInMillis();
            long rt = rhs.getTimeInMillis();
            return Long.compare(rt, lt);
        };
        FEED_ADAPTER = new DefaultFeedAdapter();
        FEED_ADAPTER_LATEST = new DefaultFeedAdapter() { // from class: hudson.model.Run.2
            @Override // hudson.model.Run.DefaultFeedAdapter
            public String getEntryID(Run e) {
                return "tag:hudson.dev.java.net,2008:" + e.getParent().getAbsoluteUrl();
            }
        };
        PERMISSIONS = new PermissionGroup(Run.class, Messages._Run_Permissions_Title());
        DELETE = new Permission(PERMISSIONS, "Delete", Messages._Run_DeletePermission_Description(), Permission.DELETE, PermissionScope.RUN);
        UPDATE = new Permission(PERMISSIONS, "Update", Messages._Run_UpdatePermission_Description(), Permission.UPDATE, PermissionScope.RUN);
        ARTIFACTS = new Permission(PERMISSIONS, "Artifacts", Messages._Run_ArtifactsPermission_Description(), (Permission) null, Functions.isArtifactsPermissionEnabled(), new PermissionScope[]{PermissionScope.RUN});
        SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(Run.class.getName() + ".skipPermissionCheck");
    }

    /* loaded from: Run$SerializableArtifact.class */
    private static final class SerializableArtifact implements Serializable {
        private static final long serialVersionUID = 1;
        final String name;
        final String relativePath;
        final String href;
        final String length;
        final String treeNodeId;

        SerializableArtifact(String name, String relativePath, String href, String length, String treeNodeId) {
            this.name = name;
            this.relativePath = relativePath;
            this.href = href;
            this.length = length;
            this.treeNodeId = treeNodeId;
        }
    }

    @SuppressFBWarnings(value = {"EQ_DOESNT_OVERRIDE_EQUALS"}, justification = "TODO needs triage")
    /* loaded from: Run$SerializableArtifactList.class */
    private static final class SerializableArtifactList extends ArrayList<SerializableArtifact> {
        private static final long serialVersionUID = 1;
        private LinkedHashMap<SerializableArtifact, String> tree = new LinkedHashMap<>();
        private int idSeq = 0;

        private SerializableArtifactList() {
        }
    }

    @SuppressFBWarnings(value = {"EQ_DOESNT_OVERRIDE_EQUALS"}, justification = "TODO needs triage")
    /* loaded from: Run$ArtifactList.class */
    public final class ArtifactList extends ArrayList<Run<JobT, RunT>.Artifact> {
        private static final long serialVersionUID = 1;
        private LinkedHashMap<Run<JobT, RunT>.Artifact, String> tree = new LinkedHashMap<>();

        public ArtifactList() {
        }

        void updateFrom(SerializableArtifactList clone) {
            Map<String, Run<JobT, RunT>.Artifact> artifacts = new HashMap<>();
            Iterator<SerializableArtifact> it = clone.iterator();
            while (it.hasNext()) {
                Run<JobT, RunT>.Artifact a = new Artifact(Run.this, it.next());
                artifacts.put(a.relativePath, a);
                add(a);
            }
            this.tree = new LinkedHashMap<>();
            for (Map.Entry<SerializableArtifact, String> entry : clone.tree.entrySet()) {
                SerializableArtifact sa = entry.getKey();
                Run<JobT, RunT>.Artifact a2 = artifacts.get(sa.relativePath);
                if (a2 == null) {
                    a2 = new Artifact(Run.this, sa);
                }
                this.tree.put(a2, entry.getValue());
            }
        }

        public Map<Run<JobT, RunT>.Artifact, String> getTree() {
            return this.tree;
        }

        /* JADX WARN: Multi-variable type inference failed */
        public void computeDisplayName() {
            int i;
            if (size() > Run.LIST_CUTOFF) {
                return;
            }
            int maxDepth = 0;
            int[] len = new int[size()];
            String[] strArr = new String[size()];
            for (int i2 = 0; i2 < strArr.length; i2++) {
                strArr[i2] = ((Artifact) get(i2)).relativePath.split("[\\\\/]+");
                maxDepth = Math.max(maxDepth, strArr[i2].length);
                len[i2] = 1;
            }
            int depth = 0;
            do {
                boolean collision = false;
                Map<String, Integer> names = new HashMap<>();
                for (int i3 = 0; i3 < strArr.length; i3++) {
                    String displayName = combineLast(strArr[i3], len[i3]);
                    Integer j = names.put(displayName, Integer.valueOf(i3));
                    if (j != null) {
                        collision = true;
                        if (j.intValue() >= 0) {
                            int intValue = j.intValue();
                            len[intValue] = len[intValue] + 1;
                        }
                        int i4 = i3;
                        len[i4] = len[i4] + 1;
                        names.put(displayName, -1);
                    }
                }
                if (!collision) {
                    break;
                }
                i = depth;
                depth++;
            } while (i < maxDepth);
            for (int i5 = 0; i5 < strArr.length; i5++) {
                ((Artifact) get(i5)).displayPath = combineLast(strArr[i5], len[i5]);
            }
        }

        private String combineLast(String[] token, int n) {
            StringBuilder buf = new StringBuilder();
            for (int i = Math.max(0, token.length - n); i < token.length; i++) {
                if (!buf.isEmpty()) {
                    buf.append('/');
                }
                buf.append(token[i]);
            }
            return buf.toString();
        }
    }

    @ExportedBean
    /* loaded from: Run$Artifact.class */
    public class Artifact {

        @Exported(visibility = 3)
        public final String relativePath;
        String displayPath;
        private String name;
        private String href;
        private String treeNodeId;
        private String length;

        Artifact(final Run this$0, SerializableArtifact clone) {
            this(clone.name, clone.relativePath, clone.href, clone.length, clone.treeNodeId);
        }

        Artifact(String name, String relativePath, String href, String len, String treeNodeId) {
            this.name = name;
            this.relativePath = relativePath;
            this.href = href;
            this.treeNodeId = treeNodeId;
            this.length = len;
        }

        @NonNull
        @Deprecated
        public File getFile() {
            return new File(Run.this.getArtifactsDir(), this.relativePath);
        }

        @Exported(visibility = 3)
        public String getFileName() {
            return this.name;
        }

        @Exported(visibility = 3)
        public String getDisplayPath() {
            return this.displayPath;
        }

        public String getHref() {
            return this.href;
        }

        public String getLength() {
            return this.length;
        }

        public long getFileSize() {
            try {
                return Long.decode(this.length).longValue();
            } catch (NumberFormatException e) {
                Run.LOGGER.log(Level.FINE, "Cannot determine file size of the artifact {0}. The length {1} is not a valid long value", new Object[]{this, this.length});
                return 0L;
            }
        }

        public String getTreeNodeId() {
            return this.treeNodeId;
        }

        public String toString() {
            return this.relativePath;
        }
    }

    @Exported(name = "fingerprint", inline = true, visibility = -1)
    @NonNull
    public Collection<Fingerprint> getBuildFingerprints() {
        Fingerprinter.FingerprintAction fingerprintAction = getAction(Fingerprinter.FingerprintAction.class);
        if (fingerprintAction != null) {
            return fingerprintAction.getFingerprints().values();
        }
        return Collections.emptyList();
    }

    @NonNull
    @Deprecated
    public File getLogFile() {
        File rawF = new File(getRootDir(), "log");
        if (rawF.isFile()) {
            return rawF;
        }
        File gzF = new File(getRootDir(), "log.gz");
        if (gzF.isFile()) {
            return gzF;
        }
        return rawF;
    }

    @NonNull
    public InputStream getLogInputStream() throws IOException {
        File logFile = getLogFile();
        if (logFile.exists()) {
            try {
                InputStream fis = Files.newInputStream(logFile.toPath(), new OpenOption[0]);
                if (logFile.getName().endsWith(".gz")) {
                    return new GZIPInputStream(fis);
                }
                return fis;
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }
        }
        String message = "No such file: " + String.valueOf(logFile);
        return new ByteArrayInputStream(this.charset != null ? message.getBytes(this.charset) : message.getBytes(Charset.defaultCharset()));
    }

    @NonNull
    public Reader getLogReader() throws IOException {
        return this.charset == null ? new InputStreamReader(getLogInputStream(), Charset.defaultCharset()) : new InputStreamReader(getLogInputStream(), this.charset);
    }

    @SuppressFBWarnings(value = {"RV_RETURN_VALUE_IGNORED"}, justification = "method signature does not permit plumbing through the return value")
    public void writeLogTo(final long offset, @NonNull XMLOutput out) throws IOException {
        AnnotatedLargeText<?> logText = getLogText();
        if (offset > 0) {
            try {
                logText.writeRawLogTo(offset - 1, new OutputStream(this) { // from class: hudson.model.Run.1
                    long pos;

                    {
                        this.pos = offset;
                    }

                    @Override // java.io.OutputStream
                    public void write(int b) throws IOException {
                        if (b == 10) {
                            throw new Halt(this.pos);
                        }
                        this.pos++;
                    }
                });
            } catch (Halt halt) {
                offset = halt.offset;
            }
        }
        logText.writeHtmlTo(offset, out.asWriter());
    }

    /* loaded from: Run$Halt.class */
    private static final class Halt extends IOException {
        final long offset;

        Halt(long offset) {
            this.offset = offset;
        }
    }

    public void writeWholeLogTo(@NonNull OutputStream out) throws IOException, InterruptedException {
        AnnotatedLargeText logText = getLogText();
        long writeLogTo = logText.writeLogTo(0L, out);
        while (true) {
            long pos = writeLogTo;
            if (!logText.isComplete()) {
                Thread.sleep(1000L);
                logText = getLogText();
                writeLogTo = logText.writeLogTo(pos, out);
            } else {
                return;
            }
        }
    }

    @NonNull
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText(getLogFile(), getCharset(), !isLogUpdated(), this);
    }

    @NonNull
    protected SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex().add("console").add("changes");
        for (Action a : getAllActions()) {
            if (a.getIconFileName() != null) {
                builder.add(a.getUrlName());
            }
        }
        return builder;
    }

    @NonNull
    public Api getApi() {
        return new Api(this);
    }

    @NonNull
    public ACL getACL() {
        return getParent().getACL();
    }

    public synchronized void deleteArtifacts() throws IOException {
        try {
            getArtifactManager().delete();
        } catch (InterruptedException x) {
            throw new IOException(x);
        }
    }

    public void delete() throws IOException {
        if (isLogUpdated()) {
            throw new IOException("Unable to delete " + String.valueOf(this) + " because it is still running");
        }
        synchronized (this) {
            if (this.isPendingDelete) {
                return;
            }
            this.isPendingDelete = true;
            File rootDir = getRootDir();
            if (!rootDir.isDirectory()) {
                LOGGER.warning(String.format("%s: %s looks to have already been deleted, assuming build dir was already cleaned up", this, rootDir));
                RunListener.fireDeleted(this);
                SaveableListener.fireOnDeleted(this, getDataFile());
                synchronized (this) {
                    removeRunFromParent();
                }
                return;
            }
            RunListener.fireDeleted(this);
            SaveableListener.fireOnDeleted(this, getDataFile());
            if (this.artifactManager != null) {
                deleteArtifacts();
            }
            synchronized (this) {
                File tmp = new File(rootDir.getParentFile(), "." + rootDir.getName());
                if (tmp.exists()) {
                    Util.deleteRecursive(tmp);
                }
                try {
                    Files.move(Util.fileToPath(rootDir), Util.fileToPath(tmp), StandardCopyOption.ATOMIC_MOVE);
                    Util.deleteRecursive(tmp);
                    if (tmp.exists()) {
                        tmp.deleteOnExit();
                    }
                    LOGGER.log(Level.FINE, "{0}: {1} successfully deleted", new Object[]{this, rootDir});
                    removeRunFromParent();
                } catch (SecurityException | UnsupportedOperationException ex) {
                    throw new IOException(String.valueOf(rootDir) + " is in use", ex);
                }
            }
        }
    }

    private void removeRunFromParent() {
        getParent().removeRun(this);
    }

    static void reportCheckpoint(@NonNull CheckPoint id) {
        Run<?, ?>.RunExecution exec = RunnerStack.INSTANCE.peek();
        if (exec == null) {
            return;
        }
        ((RunExecution) exec).checkpoints.report(id);
    }

    static void waitForCheckpoint(@NonNull CheckPoint id, @CheckForNull BuildListener listener, @CheckForNull String waiter) throws InterruptedException {
        Run b;
        while (true) {
            Run<?, ?>.RunExecution exec = RunnerStack.INSTANCE.peek();
            if (exec == null || (b = exec.getBuild().getPreviousBuildInProgress()) == null) {
                return;
            }
            RunExecution runner = b.runner;
            if (runner == null) {
                Thread.sleep(0L);
            } else if (runner.checkpoints.waitForCheckPoint(id, listener, waiter)) {
                return;
            }
        }
    }

    @Deprecated
    /* loaded from: Run$Runner.class */
    protected abstract class Runner extends Run<JobT, RunT>.RunExecution {
        protected Runner(final Run this$0) {
            super();
        }
    }

    /* loaded from: Run$RunExecution.class */
    public abstract class RunExecution {
        private final Run<JobT, RunT>.RunExecution.CheckpointSet checkpoints = new CheckpointSet();
        private final Map<Object, Object> attributes = new HashMap();

        @NonNull
        public abstract Result run(@NonNull BuildListener listener) throws Exception;

        public abstract void post(@NonNull BuildListener listener) throws Exception;

        public abstract void cleanUp(@NonNull BuildListener listener) throws Exception;

        public RunExecution() {
        }

        /* loaded from: Run$RunExecution$CheckpointSet.class */
        private final class CheckpointSet {
            private final Set<CheckPoint> checkpoints = new HashSet();
            private boolean allDone;

            private CheckpointSet() {
            }

            protected synchronized void report(@NonNull CheckPoint identifier) {
                this.checkpoints.add(identifier);
                notifyAll();
            }

            protected synchronized boolean waitForCheckPoint(@NonNull CheckPoint identifier, @CheckForNull BuildListener listener, @CheckForNull String waiter) throws InterruptedException {
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                t.setName(oldName + " : waiting for " + String.valueOf(identifier) + " on " + Run.this.getFullDisplayName() + " from " + waiter);
                boolean first = true;
                while (!this.allDone && !this.checkpoints.contains(identifier)) {
                    try {
                        if (first && listener != null && waiter != null) {
                            listener.getLogger().println(Messages.Run__is_waiting_for_a_checkpoint_on_(waiter, Run.this.getFullDisplayName()));
                        }
                        wait();
                        first = false;
                    } catch (Throwable th) {
                        t.setName(oldName);
                        throw th;
                    }
                }
                boolean contains = this.checkpoints.contains(identifier);
                t.setName(oldName);
                return contains;
            }

            private synchronized void allDone() {
                this.allDone = true;
                notifyAll();
            }
        }

        @NonNull
        public RunT getBuild() {
            return (RunT) Run.this._this();
        }

        @NonNull
        public JobT getProject() {
            return (JobT) Run.this._this().getParent();
        }

        @NonNull
        public Map<Object, Object> getAttributes() {
            return this.attributes;
        }
    }

    @Deprecated
    protected final void run(@NonNull Run<JobT, RunT>.Runner job) {
        execute(job);
    }

    protected final void execute(@NonNull Run<JobT, RunT>.RunExecution job) {
        long start;
        User usr;
        if (this.result != null) {
            return;
        }
        OutputStream logger = null;
        StreamBuildListener listener = null;
        this.runner = job;
        onStartBuilding();
        try {
            try {
                start = System.currentTimeMillis();
                try {
                    try {
                        Computer computer = Computer.currentComputer();
                        Charset charset = null;
                        if (computer != null) {
                            charset = computer.getDefaultCharset();
                            this.charset = charset.name();
                        }
                        logger = createLogger();
                        listener = createBuildListener(job, logger, charset);
                        listener.started(getCauses());
                        Authentication auth = Jenkins.getAuthentication2();
                        if (auth.equals(ACL.SYSTEM2)) {
                            listener.getLogger().println(Messages.Run_running_as_SYSTEM());
                        } else {
                            String id = auth.getName();
                            if (!auth.equals(Jenkins.ANONYMOUS2) && (usr = User.getById(id, false)) != null) {
                                id = ModelHyperlinkNote.encodeTo(usr);
                            }
                            listener.getLogger().println(Messages.Run_running_as_(id));
                        }
                        RunListener.fireStarted(this, listener);
                        setResult(job.run(listener));
                        LOGGER.log(Level.FINE, "{0} main build action completed: {1}", new Object[]{this, this.result});
                        CheckPoint.MAIN_COMPLETED.report();
                    } finally {
                    }
                } catch (RunnerAbortedException e) {
                    this.result = Result.FAILURE;
                    LOGGER.log(Level.FINE, "Build " + String.valueOf(this) + " aborted", (Throwable) e);
                } catch (AbortException e2) {
                    this.result = Result.FAILURE;
                    listener.error(e2.getMessage());
                    LOGGER.log(Level.FINE, "Build " + String.valueOf(this) + " aborted", e2);
                } catch (InterruptedException e3) {
                    this.result = Executor.currentExecutor().abortResult();
                    listener.getLogger().println(Messages.Run_BuildAborted());
                    Executor.currentExecutor().recordCauseOfInterruption(this, listener);
                    LOGGER.log(Level.INFO, String.valueOf(this) + " aborted", (Throwable) e3);
                } catch (Throwable e4) {
                    handleFatalBuildProblem(listener, e4);
                    this.result = Result.FAILURE;
                }
                job.post((BuildListener) Objects.requireNonNull(listener));
                long end = System.currentTimeMillis();
                this.duration = Math.max(end - start, 0L);
                LOGGER.log(Level.FINER, "moving into POST_PRODUCTION on {0}", this);
                this.state = State.POST_PRODUCTION;
                if (listener != null) {
                    RunListener.fireCompleted(this, listener);
                    try {
                        job.cleanUp(listener);
                    } catch (Exception e5) {
                        handleFatalBuildProblem(listener, e5);
                    }
                    listener.finished(this.result);
                    listener.closeQuietly();
                }
                try {
                    save();
                } catch (IOException e6) {
                    LOGGER.log(Level.SEVERE, "Failed to save build record", (Throwable) e6);
                }
            } catch (Throwable e7) {
                handleFatalBuildProblem(listener, e7);
                this.result = Result.FAILURE;
                long end2 = System.currentTimeMillis();
                this.duration = Math.max(end2 - start, 0L);
                LOGGER.log(Level.FINER, "moving into POST_PRODUCTION on {0}", this);
                this.state = State.POST_PRODUCTION;
                if (listener != null) {
                    RunListener.fireCompleted(this, listener);
                    try {
                        job.cleanUp(listener);
                    } catch (Exception e8) {
                        handleFatalBuildProblem(listener, e8);
                    }
                    listener.finished(this.result);
                    listener.closeQuietly();
                }
                try {
                    save();
                } catch (IOException e9) {
                    LOGGER.log(Level.SEVERE, "Failed to save build record", (Throwable) e9);
                }
            }
            onEndBuilding();
            if (logger != null) {
                try {
                    logger.close();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to close log for " + String.valueOf(this), (Throwable) x);
                }
            }
        } catch (Throwable th) {
            onEndBuilding();
            if (logger != null) {
                try {
                    logger.close();
                } catch (IOException x2) {
                    LOGGER.log(Level.WARNING, "failed to close log for " + String.valueOf(this), (Throwable) x2);
                }
            }
            throw th;
        }
    }

    private OutputStream createLogger() throws IOException {
        try {
            return Files.newOutputStream(getLogFile().toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    /* JADX WARN: Type inference failed for: r0v1, types: [hudson.model.Run] */
    private StreamBuildListener createBuildListener(@NonNull Run<JobT, RunT>.RunExecution job, OutputStream logger, Charset charset) throws IOException, InterruptedException {
        ?? build = job.getBuild();
        Iterator it = ConsoleLogFilter.all().iterator();
        while (it.hasNext()) {
            ConsoleLogFilter filter = (ConsoleLogFilter) it.next();
            logger = filter.decorateLogger((Run) build, logger);
        }
        BuildableItemWithBuildWrappers buildableItemWithBuildWrappers = this.project;
        if (buildableItemWithBuildWrappers instanceof BuildableItemWithBuildWrappers) {
            BuildableItemWithBuildWrappers biwbw = buildableItemWithBuildWrappers;
            if (build instanceof AbstractBuild) {
                AbstractBuild abstractBuild = (AbstractBuild) build;
                Iterator it2 = biwbw.getBuildWrappersList().iterator();
                while (it2.hasNext()) {
                    BuildWrapper bw = (BuildWrapper) it2.next();
                    logger = bw.decorateLogger(abstractBuild, logger);
                }
            }
        }
        return new StreamBuildListener(logger, charset);
    }

    @Deprecated
    public final void updateSymlinks(@NonNull TaskListener listener) throws InterruptedException {
    }

    private void handleFatalBuildProblem(@NonNull BuildListener listener, @NonNull Throwable e) {
        if (listener != null) {
            LOGGER.log(Level.FINE, getDisplayName() + " failed to build", e);
            if (e instanceof IOException) {
                Util.displayIOException((IOException) e, listener);
            }
            Functions.printStackTrace(e, listener.fatalError(e.getMessage()));
            return;
        }
        LOGGER.log(Level.SEVERE, getDisplayName() + " failed to build and we don't even have a listener", e);
    }

    protected void onStartBuilding() {
        LOGGER.log(Level.FINER, "moving to BUILDING on {0}", this);
        this.state = State.BUILDING;
        this.startTime = System.currentTimeMillis();
        if (this.runner != null) {
            RunnerStack.INSTANCE.push(this.runner);
        }
        RunListener.fireInitialize(this);
    }

    protected void onEndBuilding() {
        this.state = State.COMPLETED;
        LOGGER.log(Level.FINER, "moving to COMPLETED on {0}", this);
        if (this.runner != null) {
            ((RunExecution) this.runner).checkpoints.allDone();
            this.runner = null;
            RunnerStack.INSTANCE.pop();
        }
        if (this.result == null) {
            this.result = Result.FAILURE;
            LOGGER.log(Level.WARNING, "{0}: No build result is set, so marking as failure. This should not happen.", this);
        }
        RunListener.fireFinalized(this);
    }

    public synchronized void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        getDataFile().write(this);
        SaveableListener.fireOnChange(this, getDataFile());
    }

    @NonNull
    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM, new File(getRootDir(), "build.xml"));
    }

    protected Object writeReplace() {
        return XmlFile.replaceIfNotAtTopLevel(this, () -> {
            return new Replacer(this);
        });
    }

    /* loaded from: Run$Replacer.class */
    private static class Replacer {
        private final String id;

        Replacer(Run<?, ?> r) {
            this.id = r.getExternalizableId();
        }

        private Object readResolve() {
            return Run.fromExternalizableId(this.id);
        }
    }

    @NonNull
    @Deprecated
    public String getLog() throws IOException {
        return Util.loadFile(getLogFile(), getCharset());
    }

    @NonNull
    public List<String> getLog(int maxLines) throws IOException {
        if (maxLines == 0) {
            return Collections.emptyList();
        }
        int lines = 0;
        List<String> lastLines = new ArrayList<>(Math.min(maxLines, 128));
        List<Byte> bytes = new ArrayList<>();
        RandomAccessFile fileHandler = new RandomAccessFile(getLogFile(), "r");
        try {
            long fileLength = fileHandler.length() - 1;
            long filePointer = fileLength;
            while (filePointer != -1 && maxLines != lines) {
                fileHandler.seek(filePointer);
                byte readByte = fileHandler.readByte();
                if (readByte == 10) {
                    if (filePointer < fileLength) {
                        lines++;
                        lastLines.add(convertBytesToString(bytes));
                        bytes.clear();
                    }
                } else if (readByte != 13) {
                    bytes.add(Byte.valueOf(readByte));
                }
                filePointer--;
            }
            fileHandler.close();
            if (lines != maxLines) {
                lastLines.add(convertBytesToString(bytes));
            }
            Collections.reverse(lastLines);
            if (lines == maxLines) {
                lastLines.set(0, "[...truncated " + Functions.humanReadableByteSize(filePointer) + "...]");
            }
            return ConsoleNote.removeNotes(lastLines);
        } catch (Throwable th) {
            try {
                fileHandler.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private String convertBytesToString(List<Byte> bytes) {
        Collections.reverse(bytes);
        byte[] byteArray = new byte[bytes.size()];
        for (int i = 0; i < byteArray.length; i++) {
            byteArray[i] = bytes.get(i).byteValue();
        }
        return new String(byteArray, getCharset());
    }

    public void doBuildStatus(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        rsp.sendRedirect2(req.getContextPath() + "/images/48x48/" + getBuildStatusUrl());
    }

    /* loaded from: Run$Summary.class */
    public static class Summary {
        public boolean isWorse;
        public String message;

        public Summary(boolean worse, String message) {
            this.isWorse = worse;
            this.message = message;
        }
    }

    @NonNull
    public Summary getBuildStatusSummary() {
        if (isBuilding()) {
            return new Summary(false, Messages.Run_Summary_Unknown());
        }
        ResultTrend trend = ResultTrend.getResultTrend(this);
        Iterator it = ExtensionList.lookup(StatusSummarizer.class).iterator();
        while (it.hasNext()) {
            StatusSummarizer summarizer = (StatusSummarizer) it.next();
            Summary summary = summarizer.summarize(this, trend);
            if (summary != null) {
                return summary;
            }
        }
        switch (AnonymousClass3.$SwitchMap$hudson$model$ResultTrend[trend.ordinal()]) {
            case Maven.MavenInstallation.MAVEN_21 /* 1 */:
                return new Summary(false, Messages.Run_Summary_Aborted());
            case Maven.MavenInstallation.MAVEN_30 /* 2 */:
                return new Summary(false, Messages.Run_Summary_NotBuilt());
            case 3:
                return new Summary(true, Messages.Run_Summary_BrokenSinceThisBuild());
            case 4:
                RunT since = getPreviousNotFailedBuild();
                if (since == null) {
                    return new Summary(false, Messages.Run_Summary_BrokenForALongTime());
                }
                return new Summary(false, Messages.Run_Summary_BrokenSince(since.getNextBuild().getDisplayName()));
            case 5:
            case 6:
                return new Summary(false, Messages.Run_Summary_Unstable());
            case 7:
                return new Summary(true, Messages.Run_Summary_Unstable());
            case 8:
                return new Summary(false, Messages.Run_Summary_Stable());
            case 9:
                return new Summary(false, Messages.Run_Summary_BackToNormal());
            default:
                return new Summary(false, Messages.Run_Summary_Unknown());
        }
    }

    /* renamed from: hudson.model.Run$3, reason: invalid class name */
    /* loaded from: Run$3.class */
    static /* synthetic */ class AnonymousClass3 {
        static final /* synthetic */ int[] $SwitchMap$hudson$model$ResultTrend = new int[ResultTrend.values().length];

        static {
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.ABORTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.NOT_BUILT.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.FAILURE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.STILL_FAILING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.NOW_UNSTABLE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.STILL_UNSTABLE.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.UNSTABLE.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.SUCCESS.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                $SwitchMap$hudson$model$ResultTrend[ResultTrend.FIXED.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
        }
    }

    @NonNull
    public DirectoryBrowserSupport doArtifact() {
        if (Functions.isArtifactsPermissionEnabled()) {
            checkPermission(ARTIFACTS);
        }
        return new DirectoryBrowserSupport(this, getArtifactManager().root(), Messages.Run_ArtifactsBrowserTitle(this.project.getDisplayName(), getDisplayName()), "package.png", true);
    }

    public void doBuildNumber(StaplerResponse2 rsp) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(JenkinsLocationConfiguration.ORDINAL);
        rsp.getWriter().print(this.number);
    }

    public void doBuildTimestamp(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String format) throws IOException {
        DateFormat simpleDateFormat;
        rsp.setContentType("text/plain");
        rsp.setCharacterEncoding("US-ASCII");
        rsp.setStatus(JenkinsLocationConfiguration.ORDINAL);
        if (format == null) {
            simpleDateFormat = DateFormat.getDateTimeInstance(3, 3, Locale.ENGLISH);
        } else {
            simpleDateFormat = new SimpleDateFormat(format, req.getLocale());
        }
        DateFormat df = simpleDateFormat;
        rsp.getWriter().print(df.format(getTime()));
    }

    public void doConsoleText(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (Util.isOverridden(Run.class, getClass(), "doConsoleText", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            doConsoleText(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doConsoleTextImpl(req, rsp);
        }
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doConsoleText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doConsoleTextImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doConsoleTextImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        rsp.setContentType("text/plain;charset=UTF-8");
        InputStream input = getLogInputStream();
        try {
            ServletOutputStream outputStream = rsp.getOutputStream();
            try {
                PlainTextConsoleOutputStream out = new PlainTextConsoleOutputStream(outputStream);
                try {
                    IOUtils.copy(input, out);
                    out.close();
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (input != null) {
                        input.close();
                    }
                } catch (Throwable th) {
                    try {
                        out.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            } finally {
            }
        } catch (Throwable th3) {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable th4) {
                    th3.addSuppressed(th4);
                }
            }
            throw th3;
        }
    }

    @Deprecated
    public void doProgressiveLog(StaplerRequest req, StaplerResponse rsp) throws IOException {
        getLogText().doProgressText(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    public boolean canToggleLogKeep() {
        if (!this.keepLog && isKeepLog()) {
            return false;
        }
        return true;
    }

    @RequirePOST
    public void doToggleLogKeep(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        keepLog(!this.keepLog);
        rsp.forwardToPreviousPage(req);
    }

    @CLIMethod(name = "keep-build")
    public final void keepLog() throws IOException {
        keepLog(true);
    }

    public void keepLog(boolean newValue) throws IOException {
        checkPermission(newValue ? UPDATE : DELETE);
        this.keepLog = newValue;
        save();
    }

    @RequirePOST
    public void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(Run.class, getClass(), "doDoDelete", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                doDoDelete(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
                return;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
        doDoDeleteImpl(req, rsp);
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doDoDeleteImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doDoDeleteImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(DELETE);
        String why = getWhyKeepLog();
        if (why != null) {
            sendError(Messages.Run_UnableToDelete(getFullDisplayName(), why), req, rsp);
            return;
        }
        try {
            delete();
            rsp.sendRedirect2(req.getContextPath() + "/" + getParent().getUrl());
        } catch (IOException ex) {
            req.setAttribute("stackTraces", Functions.printThrowable(ex));
            req.getView(this, "delete-retry.jelly").forward(req, rsp);
        }
    }

    public void setDescription(String description) throws IOException {
        checkPermission(UPDATE);
        this.description = description;
        save();
    }

    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");
    }

    @Deprecated
    public Map<String, String> getEnvVars() {
        LOGGER.log(Level.WARNING, "deprecated call to Run.getEnvVars\n\tat {0}", new Throwable().getStackTrace()[1]);
        try {
            return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        } catch (IOException | InterruptedException e) {
            return new EnvVars();
        }
    }

    @Deprecated
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        LOGGER.log(Level.WARNING, "deprecated call to Run.getEnvironment\n\tat {0}", new Throwable().getStackTrace()[1]);
        return getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
    }

    @NonNull
    public EnvVars getEnvironment(@NonNull TaskListener listener) throws IOException, InterruptedException {
        Computer c = Computer.currentComputer();
        Node n = c == null ? null : c.getNode();
        EnvVars env = getParent().getEnvironment(n, listener);
        env.putAll(getCharacteristicEnvVars());
        for (EnvironmentContributor ec : EnvironmentContributor.all().reverseView()) {
            ec.buildEnvironmentFor(this, env, listener);
        }
        if (!(this instanceof AbstractBuild)) {
            for (EnvironmentContributingAction a : getActions(EnvironmentContributingAction.class)) {
                a.buildEnvironment(this, env);
            }
        }
        return env;
    }

    @NonNull
    public final EnvVars getCharacteristicEnvVars() {
        EnvVars env = getParent().getCharacteristicEnvVars();
        env.put("BUILD_NUMBER", String.valueOf(this.number));
        env.put("BUILD_ID", getId());
        env.put("BUILD_TAG", "jenkins-" + getParent().getFullName().replace('/', '-') + "-" + this.number);
        return env;
    }

    @NonNull
    public String getExternalizableId() {
        return this.project.getFullName() + "#" + getNumber();
    }

    @CheckForNull
    public static Run<?, ?> fromExternalizableId(String id) throws IllegalArgumentException, AccessDeniedException {
        int hash = id.lastIndexOf(35);
        if (hash <= 0) {
            throw new IllegalArgumentException("Invalid id");
        }
        String jobName = id.substring(0, hash);
        try {
            int number = Integer.parseInt(id.substring(hash + 1));
            Jenkins j = Jenkins.getInstanceOrNull();
            if (j == null) {
                LOGGER.fine(() -> {
                    return "Jenkins not running";
                });
                return null;
            }
            Job<?, ?> job = (Job) j.getItemByFullName(jobName, Job.class);
            if (job == null) {
                LOGGER.fine(() -> {
                    return "no such job " + jobName + " when running as " + Jenkins.getAuthentication2().getName();
                });
                return null;
            }
            return job.mo10getBuildByNumber(number);
        } catch (NumberFormatException x) {
            throw new IllegalArgumentException(x);
        }
    }

    @Exported
    public long getEstimatedDuration() {
        return this.project.getEstimatedDuration();
    }

    @POST
    @NonNull
    public HttpResponse doConfigSubmit(StaplerRequest2 req) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(UPDATE);
        BulkChange bc = new BulkChange(this);
        try {
            JSONObject json = req.getSubmittedForm();
            submit(json);
            bc.commit();
            bc.close();
            return FormApply.success(".");
        } catch (Throwable th) {
            try {
                bc.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    protected void submit(JSONObject json) throws IOException {
        setDisplayName(Util.fixEmptyAndTrim(json.getString("displayName")));
        setDescription(json.getString("description"));
    }

    /* loaded from: Run$KeepLogBuildBadge.class */
    public final class KeepLogBuildBadge implements BuildBadgeAction {
        public KeepLogBuildBadge() {
        }

        @CheckForNull
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        public String getUrlName() {
            return null;
        }

        @CheckForNull
        public String getWhyKeepLog() {
            return Run.this.getWhyKeepLog();
        }
    }

    /* loaded from: Run$DefaultFeedAdapter.class */
    private static class DefaultFeedAdapter implements FeedAdapter<Run> {
        private DefaultFeedAdapter() {
        }

        public String getEntryTitle(Run entry) {
            return entry.getFullDisplayName() + " (" + entry.getBuildStatusSummary().message + ")";
        }

        public String getEntryUrl(Run entry) {
            return entry.getUrl();
        }

        @Override // 
        public String getEntryID(Run entry) {
            return "tag:hudson.dev.java.net," + entry.getTimestamp().get(1) + ":" + entry.getParent().getFullName() + ":" + entry.getId();
        }

        public String getEntryDescription(Run entry) {
            return entry.getDescription();
        }

        public Calendar getEntryTimestamp(Run entry) {
            return entry.getTimestamp();
        }

        public String getEntryAuthor(Run entry) {
            return JenkinsLocationConfiguration.get().getAdminAddress();
        }
    }

    public Object getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        if (Util.isOverridden(Run.class, getClass(), "getDynamic", new Class[]{String.class, StaplerRequest.class, StaplerResponse.class})) {
            return getDynamic(token, StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        }
        Object returnedResult = super.getDynamic(token, req, rsp);
        return getDynamicImpl(token, returnedResult);
    }

    @Deprecated
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        Object returnedResult = super.getDynamic(token, req, rsp);
        return getDynamicImpl(token, returnedResult);
    }

    private Object getDynamicImpl(String token, Object returnedResult) {
        if (returnedResult == null) {
            for (Action action : getTransientActions()) {
                String urlName = action.getUrlName();
                if (urlName != null && urlName.equals(token)) {
                    return action;
                }
            }
            returnedResult = new RedirectUp();
        }
        return returnedResult;
    }

    @Restricted({NoExternalUse.class})
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            if (!getParent().hasPermission(Item.DISCOVER)) {
                return null;
            }
            getParent().checkPermission(Item.READ);
        }
        return this;
    }

    /* loaded from: Run$RedirectUp.class */
    public static class RedirectUp {
        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            rsp.setStatus(404);
            rsp.setContentType("text/html;charset=UTF-8");
            PrintWriter out = rsp.getWriter();
            Util.printRedirect(req.getContextPath(), "..", "Not found", out);
            out.flush();
        }
    }

    @Extension
    /* loaded from: Run$BasicRunDetailFactory.class */
    public static final class BasicRunDetailFactory extends DetailFactory<Run> {
        public Class<Run> type() {
            return Run.class;
        }

        @NonNull
        public List<? extends Detail> createFor(@NonNull Run target) {
            return List.of(new CauseDetail(target), new TimestampDetail(target), new DurationDetail(target), new KeptForeverDetail(target));
        }
    }

    @Restricted({NoExternalUse.class})
    public List<Tab> getRunTabs() {
        return getActions(Tab.class).stream().filter(e -> {
            return e.getIconFileName() != null;
        }).toList();
    }
}
