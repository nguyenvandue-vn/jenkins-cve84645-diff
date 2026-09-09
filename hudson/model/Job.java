package hudson.model;

import com.infradna.tool.bridge_method_injector.BridgeMethodsAdded;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FeedAdapter;
import hudson.PermalinkList;
import hudson.Util;
import hudson.cli.declarative.CLIResolver;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.search.QuickSilver;
import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.search.SearchItems;
import hudson.security.ACL;
import hudson.tasks.LogRotator;
import hudson.tasks.Maven;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.CopyOnWriteList;
import hudson.util.DataSetBuilder;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.Graph;
import hudson.util.RunList;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import hudson.util.TextFile;
import hudson.widgets.HistoryWidget;
import hudson.widgets.Widget;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.awt.Color;
import java.awt.Paint;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.HistoricalBuild;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.PeepholePermalink;
import jenkins.model.ProjectNamingStrategy;
import jenkins.model.Tab;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.model.details.DownstreamProjectsDetail;
import jenkins.model.details.ProjectNameDetail;
import jenkins.model.details.UpstreamProjectsDetail;
import jenkins.scm.RunWithSCM;
import jenkins.security.HexStringConfidentialKey;
import jenkins.security.XStreamNotDeserializable;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.triggers.SCMTriggerItem;
import jenkins.widgets.HasWidgets;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.stapler.StaplerOverridable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

@BridgeMethodsAdded
/* loaded from: Job.class */
public abstract class Job<JobT extends Job<JobT, RunT>, RunT extends Run<JobT, RunT>> extends AbstractItem implements ExtensionPoint, StaplerOverridable, ModelObjectWithChildren, HasWidgets {
    protected volatile transient int nextBuildNumber;

    @XStreamNotDeserializable
    private volatile transient boolean holdOffBuildUntilSave;

    @XStreamNotDeserializable
    private volatile transient boolean holdOffBuildUntilUserSave;

    @Deprecated
    private volatile BuildDiscarder logRotator;
    private transient Integer cachedBuildHealthReportsBuildNumber;
    private transient List<HealthReport> cachedBuildHealthReports;
    boolean keepDependencies;
    protected CopyOnWriteList<JobProperty<? super JobT>> properties;
    private static final Logger LOGGER = Logger.getLogger(Job.class.getName());
    public static final HistoryWidget.Adapter<HistoricalBuild> HISTORY_ADAPTER = new HistoryWidget.Adapter<HistoricalBuild>() { // from class: hudson.model.Job.2
        public int compare(HistoricalBuild record, String key) {
            try {
                int k = Integer.parseInt(key);
                return record.getNumber() - k;
            } catch (NumberFormatException e) {
                return String.valueOf(record.getNumber()).compareTo(key);
            }
        }

        public String getKey(HistoricalBuild record) {
            return String.valueOf(record.getNumber());
        }

        public boolean isBuilding(HistoricalBuild record) {
            return record.isBuilding();
        }

        public String getNextKey(String key) {
            try {
                int k = Integer.parseInt(key);
                return String.valueOf(k + 1);
            } catch (NumberFormatException e) {
                return "-unable to determine next key-";
            }
        }
    };
    private static final HexStringConfidentialKey SERVER_COOKIE = new HexStringConfidentialKey(Job.class, "serverCookie", 16);

    @Exported
    public abstract boolean isBuildable();

    /* renamed from: _getRuns */
    protected abstract SortedMap<Integer, ? extends RunT> mo7_getRuns();

    protected abstract void removeRun(RunT run);

    protected Job(ItemGroup parent, String name) {
        super(parent, name);
        this.nextBuildNumber = 1;
        this.cachedBuildHealthReportsBuildNumber = null;
        this.cachedBuildHealthReports = null;
        this.properties = new CopyOnWriteList<>();
    }

    @Override // hudson.model.AbstractItem
    public synchronized void save() throws IOException {
        super.save();
        this.holdOffBuildUntilSave = this.holdOffBuildUntilUserSave;
    }

    @Override // hudson.model.AbstractItem
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        Path buildDirPath = getBuildDir().toPath();
        Path legacyIds = buildDirPath.resolve("legacyIds");
        if (Files.exists(legacyIds, new LinkOption[0])) {
            LOGGER.info("Deleting legacyIds file in " + String.valueOf(buildDirPath) + ". See https://issues.jenkins.io/browse/JENKINS-75465 for more information.");
            Files.delete(legacyIds);
        }
        TextFile f = getNextBuildNumberFile();
        if (f.exists()) {
            try {
                synchronized (this) {
                    this.nextBuildNumber = Integer.parseInt(f.readTrim());
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Corruption in {0}: {1}", new Object[]{f, e});
                RunT lB = mo6getLastBuild();
                synchronized (this) {
                    this.nextBuildNumber = lB != null ? lB.getNumber() + 1 : 1;
                    saveNextBuildNumber();
                }
            }
        } else if (this.nextBuildNumber == 0) {
            this.nextBuildNumber = 1;
        }
        if (this.properties == null) {
            this.properties = new CopyOnWriteList<>();
        }
        Iterator it = this.properties.iterator();
        while (it.hasNext()) {
            JobProperty p = (JobProperty) it.next();
            p.setOwner(this);
        }
    }

    @Override // hudson.model.AbstractItem
    public void onCopiedFrom(Item src) {
        super.onCopiedFrom(src);
        synchronized (this) {
            this.nextBuildNumber = 1;
            this.holdOffBuildUntilUserSave = true;
            this.holdOffBuildUntilSave = this.holdOffBuildUntilUserSave;
        }
    }

    @Extension(ordinal = -1.7976931348623157E308d)
    /* loaded from: Job$LastItemListener.class */
    public static class LastItemListener extends ItemListener {
        public void onCopied(Item src, Item item) {
            if (item instanceof Job) {
                Job job = (Job) item;
                synchronized (job) {
                    job.holdOffBuildUntilUserSave = false;
                }
            }
        }
    }

    TextFile getNextBuildNumberFile() {
        return new TextFile(new File(getRootDir(), "nextBuildNumber"));
    }

    public synchronized boolean isHoldOffBuildUntilSave() {
        return this.holdOffBuildUntilSave;
    }

    protected synchronized void saveNextBuildNumber() throws IOException {
        if (this.nextBuildNumber == 0) {
            this.nextBuildNumber = 1;
        }
        getNextBuildNumberFile().write(String.valueOf(this.nextBuildNumber) + "\n");
    }

    @Exported
    public boolean isInQueue() {
        return false;
    }

    @Exported
    public Queue.Item getQueueItem() {
        return null;
    }

    public boolean isBuilding() {
        RunT b = mo6getLastBuild();
        return b != null && b.isBuilding();
    }

    public boolean isLogUpdated() {
        RunT b = mo6getLastBuild();
        return b != null && b.isLogUpdated();
    }

    @Override // hudson.model.AbstractItem
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.Job_Pronoun());
    }

    @Override // hudson.model.AbstractItem
    public boolean isNameEditable() {
        return true;
    }

    @Exported
    public boolean isKeepDependencies() {
        return this.keepDependencies;
    }

    public int assignBuildNumber() throws IOException {
        return ((BuildNumberAssigner) ExtensionList.lookupFirst(BuildNumberAssigner.class)).assignBuildNumber(this, this::saveNextBuildNumber);
    }

    @Extension(ordinal = -1000.0d)
    @Restricted({DoNotUse.class})
    /* loaded from: Job$DefaultBuildNumberAssigner.class */
    public static final class DefaultBuildNumberAssigner implements BuildNumberAssigner {
        public int assignBuildNumber(Job<?, ?> job, BuildNumberAssigner.SaveNextBuildNumber saveNextBuildNumber) throws IOException {
            int r;
            synchronized (job) {
                r = job.nextBuildNumber;
                job.nextBuildNumber = r + 1;
                saveNextBuildNumber.call();
            }
            return r;
        }
    }

    @Exported
    public int getNextBuildNumber() {
        return this.nextBuildNumber;
    }

    public EnvVars getCharacteristicEnvVars() {
        EnvVars env = new EnvVars();
        env.put("JENKINS_SERVER_COOKIE", SERVER_COOKIE.get());
        env.put("HUDSON_SERVER_COOKIE", SERVER_COOKIE.get());
        env.put("JOB_NAME", getFullName());
        env.put("JOB_BASE_NAME", getName());
        return env;
    }

    @NonNull
    public EnvVars getEnvironment(@CheckForNull Node node, @NonNull TaskListener listener) throws IOException, InterruptedException {
        Computer computer;
        EnvVars env = new EnvVars();
        if (node != null && (computer = node.toComputer()) != null) {
            env = computer.getEnvironment();
            env.putAll(computer.buildEnvironment(listener));
        }
        env.putAll(getCharacteristicEnvVars());
        env.put("CLASSPATH", "");
        for (EnvironmentContributor ec : EnvironmentContributor.all().reverseView()) {
            ec.buildEnvironmentFor(this, env, listener);
        }
        return env;
    }

    public synchronized void updateNextBuildNumber(int next) throws IOException {
        RunT lb = mo6getLastBuild();
        if (lb != null) {
            if (next <= lb.getNumber()) {
                return;
            }
        } else if (next <= 0) {
            return;
        }
        this.nextBuildNumber = next;
        saveNextBuildNumber();
    }

    @Restricted({Beta.class})
    public void fastUpdateNextBuildNumber(int nextBuildNumber) {
        this.nextBuildNumber = nextBuildNumber;
    }

    public synchronized BuildDiscarder getBuildDiscarder() {
        BuildDiscarderProperty prop = _getProperty(BuildDiscarderProperty.class);
        return prop != null ? prop.getStrategy() : this.logRotator;
    }

    public synchronized void setBuildDiscarder(BuildDiscarder bd) throws IOException {
        BulkChange bc = new BulkChange(this);
        try {
            removeProperty(BuildDiscarderProperty.class);
            if (bd != null) {
                addProperty(new BuildDiscarderProperty(bd));
            }
            bc.commit();
            bc.close();
        } catch (Throwable th) {
            try {
                bc.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    @Deprecated
    public LogRotator getLogRotator() {
        LogRotator buildDiscarder = getBuildDiscarder();
        if (buildDiscarder instanceof LogRotator) {
            return buildDiscarder;
        }
        return null;
    }

    @Deprecated
    public void setLogRotator(LogRotator logRotator) throws IOException {
        setBuildDiscarder(logRotator);
    }

    public void logRotate() throws IOException, InterruptedException {
        BuildDiscarder bd = getBuildDiscarder();
        if (bd != null) {
            bd.perform(this);
        }
    }

    public boolean supportsLogRotator() {
        return true;
    }

    public String getSearchIcon() {
        return "symbol-status-" + getIconColor().getIconName();
    }

    protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex().add(new SearchIndex() { // from class: hudson.model.Job.1
            public void find(String token, List<SearchItem> result) {
                try {
                    if (token.startsWith("#")) {
                        token = token.substring(1);
                    }
                    int n = Integer.parseInt(token);
                    Run b = Job.this.mo10getBuildByNumber(n);
                    if (b == null) {
                        return;
                    }
                    result.add(SearchItems.create("#" + n, n, b));
                } catch (NumberFormatException e) {
                }
            }

            public void suggest(String token, List<SearchItem> result) {
                find(token, result);
            }
        });
    }

    @Override // hudson.model.AbstractItem
    public Collection<? extends Job> getAllJobs() {
        return Set.of(this);
    }

    public void addProperty(JobProperty<? super JobT> jobProp) throws IOException {
        jobProp.setOwner(this);
        this.properties.add(jobProp);
        save();
    }

    public void removeProperty(JobProperty<? super JobT> jobProp) throws IOException {
        this.properties.remove(jobProp);
        save();
    }

    public <T extends JobProperty> T removeProperty(Class<T> clazz) throws IOException {
        Iterator it = this.properties.iterator();
        while (it.hasNext()) {
            JobProperty<? super JobT> p = (JobProperty) it.next();
            if (clazz.isInstance(p)) {
                removeProperty(p);
                return clazz.cast(p);
            }
        }
        return null;
    }

    public Map<JobPropertyDescriptor, JobProperty<? super JobT>> getProperties() {
        Map result = Descriptor.toMap(this.properties);
        if (this.logRotator != null) {
            result.put(Jenkins.get().getDescriptorByType(BuildDiscarderProperty.DescriptorImpl.class), new BuildDiscarderProperty(this.logRotator));
        }
        return result;
    }

    @Exported(name = "property", inline = true)
    public List<JobProperty<? super JobT>> getAllProperties() {
        return this.properties.getView();
    }

    public <T extends JobProperty> T getProperty(Class<T> cls) {
        if (cls == BuildDiscarderProperty.class && this.logRotator != null) {
            return cls.cast(new BuildDiscarderProperty(this.logRotator));
        }
        return (T) _getProperty(cls);
    }

    private <T extends JobProperty> T _getProperty(Class<T> clazz) {
        Iterator it = this.properties.iterator();
        while (it.hasNext()) {
            JobProperty p = (JobProperty) it.next();
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    public JobProperty getProperty(String className) {
        Iterator it = this.properties.iterator();
        while (it.hasNext()) {
            JobProperty p = (JobProperty) it.next();
            if (p.getClass().getName().equals(className)) {
                return p;
            }
        }
        return null;
    }

    public Collection<?> getOverrides() {
        ArrayList arrayList = new ArrayList();
        Iterator it = this.properties.iterator();
        while (it.hasNext()) {
            JobProperty<? super JobT> p = (JobProperty) it.next();
            arrayList.addAll(p.getJobOverrides());
        }
        return arrayList;
    }

    @Deprecated(forRemoval = true, since = "2.410")
    protected HistoryWidget createHistoryWidget() {
        throw new IllegalStateException("HistoryWidget is now created via WidgetFactory implementation");
    }

    @Override // hudson.model.AbstractItem
    public void renameTo(String newName) throws IOException {
        File oldBuildDir = getBuildDir();
        super.renameTo(newName);
        File newBuildDir = getBuildDir();
        if (Files.isDirectory(Util.fileToPath(oldBuildDir), new LinkOption[0]) && !Files.isDirectory(Util.fileToPath(newBuildDir), new LinkOption[0])) {
            Util.createDirectories(Util.fileToPath(newBuildDir.getParentFile()), new FileAttribute[0]);
            Files.move(Util.fileToPath(oldBuildDir), Util.fileToPath(newBuildDir), new CopyOption[0]);
        }
    }

    @Override // hudson.model.AbstractItem
    public void movedTo(DirectlyModifiableTopLevelItemGroup destination, AbstractItem newItem, File destDir) throws IOException {
        File oldBuildDir = getBuildDir();
        super.movedTo(destination, newItem, destDir);
        File newBuildDir = getBuildDir();
        if (oldBuildDir.isDirectory()) {
            FileUtils.moveDirectory(oldBuildDir, newBuildDir);
        }
    }

    @Override // hudson.model.AbstractItem
    public void delete() throws IOException, InterruptedException {
        super.delete();
        Util.deleteRecursive(getBuildDir());
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: Job$SubItemBuildsLocationImpl.class */
    public static class SubItemBuildsLocationImpl extends ItemListener {
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            Jenkins jenkins2 = Jenkins.get();
            if (!jenkins2.isDefaultBuildDir() && (item instanceof Job)) {
                File newBuildDir = ((Job) item).getBuildDir();
                try {
                    if (!Util.isDescendant(item.getRootDir(), newBuildDir)) {
                        String oldBuildsDir = Jenkins.expandVariablesForDirectory(jenkins2.getRawBuildsDir(), oldFullName, "<NOPE>");
                        if (oldBuildsDir.contains("<NOPE>")) {
                            Job.LOGGER.severe(String.format("Builds directory for job %1$s appears to be outside of item root, but somehow still containing the item root path, which is unknown. Cannot move builds from %2$s to %1$s.", newFullName, oldFullName));
                        } else {
                            File oldDir = new File(oldBuildsDir);
                            if (oldDir.isDirectory()) {
                                try {
                                    FileUtils.moveDirectory(oldDir, newBuildDir);
                                } catch (IOException e) {
                                    Job.LOGGER.log(Level.SEVERE, String.format("Failed to move %s to %s", oldBuildsDir, newBuildDir.getAbsolutePath()), (Throwable) e);
                                }
                            }
                        }
                    }
                } catch (IOException e2) {
                    Job.LOGGER.log(Level.WARNING, "Failed to inspect " + String.valueOf(item.getRootDir()) + ". Builds might not be moved.", (Throwable) e2);
                }
            }
        }
    }

    @Exported(name = "allBuilds", visibility = -2)
    @WithBridgeMethods({List.class})
    /* renamed from: getBuilds, reason: merged with bridge method [inline-methods] */
    public RunList<RunT> m28getBuilds() {
        return RunList.fromRuns(mo7_getRuns().values());
    }

    @Exported(name = "builds")
    public RunList<RunT> getNewBuilds() {
        return m28getBuilds().limit(100);
    }

    public synchronized List<RunT> getBuilds(Fingerprint.RangeSet rs) {
        ArrayList arrayList = new ArrayList();
        for (Fingerprint.Range r : rs.getRanges()) {
            Run mo9getNearestBuild = mo9getNearestBuild(r.start);
            while (true) {
                Run run = mo9getNearestBuild;
                if (run != null && run.getNumber() < r.end) {
                    arrayList.add(run);
                    mo9getNearestBuild = run.getNextBuild();
                }
            }
        }
        return arrayList;
    }

    public SortedMap<Integer, RunT> getBuildsAsMap() {
        return Collections.unmodifiableSortedMap(mo7_getRuns());
    }

    /* renamed from: getBuild */
    public RunT mo11getBuild(String id) {
        for (RunT r : mo7_getRuns().values()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /* renamed from: getBuildByNumber */
    public RunT mo10getBuildByNumber(int n) {
        return mo7_getRuns().get(Integer.valueOf(n));
    }

    @Deprecated
    @WithBridgeMethods({List.class})
    /* renamed from: getBuildsByTimestamp, reason: merged with bridge method [inline-methods] */
    public RunList<RunT> m29getBuildsByTimestamp(long start, long end) {
        return m28getBuilds().byTimestamp(start, end);
    }

    @CLIResolver
    public RunT getBuildForCLI(@Argument(required = true, metaVar = "BUILD#", usage = "Build number") String id) throws CmdLineException {
        try {
            int n = Integer.parseInt(id);
            RunT r = mo10getBuildByNumber(n);
            if (r == null) {
                throw new CmdLineException((CmdLineParser) null, "No such build '#" + n + "' exists");
            }
            return r;
        } catch (NumberFormatException e) {
            throw new CmdLineException((CmdLineParser) null, id + "is not a number", e);
        }
    }

    /* renamed from: getNearestBuild */
    public RunT mo9getNearestBuild(int n) {
        SortedMap<Integer, ? extends RunT> m = mo7_getRuns().headMap(Integer.valueOf(n - 1));
        if (m.isEmpty()) {
            return null;
        }
        return m.get(m.lastKey());
    }

    /* renamed from: getNearestOldBuild */
    public RunT mo8getNearestOldBuild(int n) {
        SortedMap<Integer, ? extends RunT> m = mo7_getRuns().tailMap(Integer.valueOf(n));
        if (m.isEmpty()) {
            return null;
        }
        return m.get(m.firstKey());
    }

    public Object getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        try {
            return mo10getBuildByNumber(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            for (Widget w : getWidgets()) {
                if (w.getUrlName().equals(token)) {
                    return w;
                }
            }
            Iterator it = getPermalinks().iterator();
            while (it.hasNext()) {
                PermalinkProjectAction.Permalink p = (PermalinkProjectAction.Permalink) it.next();
                if (p.getId().equals(token)) {
                    return p.resolve(this);
                }
            }
            return super.getDynamic(token, req, rsp);
        }
    }

    public File getBuildDir() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return new File(getRootDir(), "builds");
        }
        return j.getBuildDirFor(this);
    }

    @Exported
    @QuickSilver
    /* renamed from: getLastBuild */
    public RunT mo6getLastBuild() {
        SortedMap<Integer, ? extends RunT> runs = mo7_getRuns();
        if (runs.isEmpty()) {
            return null;
        }
        return runs.get(runs.firstKey());
    }

    @Exported
    @QuickSilver
    /* renamed from: getFirstBuild */
    public RunT mo5getFirstBuild() {
        SortedMap<Integer, ? extends RunT> runs = mo7_getRuns();
        if (runs.isEmpty()) {
            return null;
        }
        return runs.get(runs.lastKey());
    }

    @Exported
    @QuickSilver
    public RunT getLastSuccessfulBuild() {
        return (RunT) PeepholePermalink.LAST_SUCCESSFUL_BUILD.resolve(this);
    }

    @Exported
    @QuickSilver
    public RunT getLastUnsuccessfulBuild() {
        return (RunT) PeepholePermalink.LAST_UNSUCCESSFUL_BUILD.resolve(this);
    }

    @Exported
    @QuickSilver
    public RunT getLastUnstableBuild() {
        return (RunT) PeepholePermalink.LAST_UNSTABLE_BUILD.resolve(this);
    }

    @Exported
    @QuickSilver
    public RunT getLastStableBuild() {
        return (RunT) PeepholePermalink.LAST_STABLE_BUILD.resolve(this);
    }

    @Exported
    @QuickSilver
    public RunT getLastFailedBuild() {
        return (RunT) PeepholePermalink.LAST_FAILED_BUILD.resolve(this);
    }

    @Exported
    @QuickSilver
    public RunT getLastCompletedBuild() {
        return (RunT) PeepholePermalink.LAST_COMPLETED_BUILD.resolve(this);
    }

    public List<RunT> getLastBuildsOverThreshold(int numberOfBuilds, Result threshold) {
        RunT r = mo6getLastBuild();
        if (r == null) {
            return Collections.emptyList();
        }
        return r.getBuildsOverThreshold(numberOfBuilds, threshold);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v26, types: [hudson.model.Run] */
    protected List<RunT> getEstimatedDurationCandidates() {
        ArrayList arrayList = new ArrayList(3);
        RunT lastSuccessful = getLastSuccessfulBuild();
        int lastSuccessfulNumber = -1;
        if (lastSuccessful != null) {
            arrayList.add(lastSuccessful);
            lastSuccessfulNumber = lastSuccessful.getNumber();
        }
        int i = 0;
        List<RunT> fallbackCandidates = new ArrayList<>(3);
        for (RunT r = mo6getLastBuild(); r != null && arrayList.size() < 3 && i < 6; r = r.getPreviousBuild()) {
            if (!r.isBuilding() && r.getResult() != null && r.getNumber() != lastSuccessfulNumber) {
                Result result = r.getResult();
                if (result.isBetterOrEqualTo(Result.UNSTABLE)) {
                    arrayList.add(r);
                } else if (result.isCompleteBuild()) {
                    fallbackCandidates.add(r);
                }
            }
            i++;
        }
        while (arrayList.size() < 3 && !fallbackCandidates.isEmpty()) {
            arrayList.add((Run) fallbackCandidates.removeFirst());
        }
        return arrayList;
    }

    public long getEstimatedDuration() {
        List<RunT> builds = getEstimatedDurationCandidates();
        if (builds.isEmpty()) {
            return -1L;
        }
        long totalDuration = 0;
        for (RunT b : builds) {
            totalDuration += b.getDuration();
        }
        if (totalDuration == 0) {
            return -1L;
        }
        return Math.round(totalDuration / builds.size());
    }

    public PermalinkList getPermalinks() {
        PeepholePermalink.initialized();
        PermalinkList permalinks = new PermalinkList(PermalinkProjectAction.Permalink.BUILTIN);
        for (PermalinkProjectAction permalinkProjectAction : getActions()) {
            if (permalinkProjectAction instanceof PermalinkProjectAction) {
                PermalinkProjectAction ppa = permalinkProjectAction;
                permalinks.addAll(ppa.getPermalinks());
            }
        }
        return permalinks;
    }

    /* renamed from: hudson.model.Job$1FeedItem, reason: invalid class name */
    /* loaded from: Job$1FeedItem.class */
    class C1FeedItem {
        ChangeLogSet.Entry e;
        int idx;

        C1FeedItem(final Job this$0, ChangeLogSet.Entry e, int idx) {
            this.e = e;
            this.idx = idx;
        }

        Run<?, ?> getBuild() {
            return this.e.getParent().build;
        }
    }

    public void doRssChangelog(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        List<C1FeedItem> entries = new ArrayList<>();
        String scmDisplayName = "";
        if (this instanceof SCMTriggerItem) {
            SCMTriggerItem scmItem = (SCMTriggerItem) this;
            List<String> scmNames = new ArrayList<>();
            for (SCM s : scmItem.getSCMs()) {
                scmNames.add(s.getDescriptor().getDisplayName());
            }
            scmDisplayName = " " + String.join(", ", scmNames);
        }
        RunWithSCM mo6getLastBuild = mo6getLastBuild();
        while (true) {
            RunWithSCM runWithSCM = mo6getLastBuild;
            if (runWithSCM != null) {
                int idx = 0;
                if (runWithSCM instanceof RunWithSCM) {
                    for (ChangeLogSet<? extends ChangeLogSet.Entry> c : runWithSCM.getChangeSets()) {
                        Iterator it = c.iterator();
                        while (it.hasNext()) {
                            ChangeLogSet.Entry e = (ChangeLogSet.Entry) it.next();
                            int i = idx;
                            idx++;
                            entries.add(new C1FeedItem(this, e, i));
                        }
                    }
                }
                mo6getLastBuild = runWithSCM.getPreviousBuild();
            } else {
                RSS.forwardToRss(getDisplayName() + scmDisplayName + " changes", getUrl() + "changes", entries, new FeedAdapter<C1FeedItem>(this) { // from class: hudson.model.Job.3
                    public String getEntryTitle(C1FeedItem item) {
                        return "#" + item.getBuild().number + " " + item.e.getMsg() + " (" + String.valueOf(item.e.getAuthor()) + ")";
                    }

                    public String getEntryUrl(C1FeedItem item) {
                        return item.getBuild().getUrl() + "changes#detail" + item.idx;
                    }

                    public String getEntryID(C1FeedItem item) {
                        return getEntryUrl(item);
                    }

                    public String getEntryDescription(C1FeedItem item) {
                        StringBuilder buf = new StringBuilder();
                        for (String path : item.e.getAffectedPaths()) {
                            buf.append(path).append('\n');
                        }
                        return buf.toString();
                    }

                    public Calendar getEntryTimestamp(C1FeedItem item) {
                        return item.getBuild().getTimestamp();
                    }

                    public String getEntryAuthor(C1FeedItem entry) {
                        return JenkinsLocationConfiguration.get().getAdminAddress();
                    }
                }, req, rsp);
                return;
            }
        }
    }

    public ModelObjectWithContextMenu.ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        if (Util.isOverridden(Job.class, getClass(), "doChildrenContextMenu", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            return doChildrenContextMenu(StaplerRequest.fromStaplerRequest2(request), StaplerResponse.fromStaplerResponse2(response));
        }
        return doChildrenContextMenuImpl(request, response);
    }

    @StaplerNotDispatchable
    @Deprecated
    public ModelObjectWithContextMenu.ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return doChildrenContextMenuImpl(StaplerRequest.toStaplerRequest2(request), StaplerResponse.toStaplerResponse2(response));
    }

    private ModelObjectWithContextMenu.ContextMenu doChildrenContextMenuImpl(StaplerRequest2 request, StaplerResponse2 response) {
        ModelObjectWithContextMenu.ContextMenu menu = new ModelObjectWithContextMenu.ContextMenu();
        Iterator it = getPermalinks().iterator();
        while (it.hasNext()) {
            PermalinkProjectAction.Permalink p = (PermalinkProjectAction.Permalink) it.next();
            if (p.resolve(this) != null) {
                menu.add(p.getId(), p.getDisplayName());
            }
        }
        return menu;
    }

    @Exported(visibility = Maven.MavenInstallation.MAVEN_30, name = "color")
    public BallColor getIconColor() {
        Run run;
        Run mo6getLastBuild = mo6getLastBuild();
        while (true) {
            run = mo6getLastBuild;
            if (run == null || !run.hasntStartedYet()) {
                break;
            }
            mo6getLastBuild = run.getPreviousBuild();
        }
        if (run != null) {
            return run.getIconColor();
        }
        return BallColor.NOTBUILT;
    }

    public HealthReport getBuildHealth() {
        List<HealthReport> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReport() : (HealthReport) reports.getFirst();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v48, types: [hudson.model.Run] */
    @Exported(name = "healthReport")
    public List<HealthReport> getBuildHealthReports() {
        List<HealthReport> reports = new ArrayList<>();
        RunT lastBuild = mo6getLastBuild();
        if (lastBuild != null && lastBuild.isBuilding()) {
            lastBuild = lastBuild.getPreviousBuild();
        }
        if (this.cachedBuildHealthReportsBuildNumber != null && this.cachedBuildHealthReports != null && lastBuild != null && this.cachedBuildHealthReportsBuildNumber.intValue() == lastBuild.getNumber()) {
            reports.addAll(this.cachedBuildHealthReports);
        } else if (lastBuild != null) {
            for (HealthReportingAction healthReportingAction : lastBuild.getActions(HealthReportingAction.class)) {
                HealthReport report = healthReportingAction.getBuildHealth();
                if (report != null) {
                    if (report.isAggregateReport()) {
                        reports.addAll(report.getAggregatedReports());
                    } else {
                        reports.add(report);
                    }
                }
            }
            HealthReport report2 = getBuildStabilityHealthReport();
            if (report2 != null) {
                if (report2.isAggregateReport()) {
                    reports.addAll(report2.getAggregatedReports());
                } else {
                    reports.add(report2);
                }
            }
            Collections.sort(reports);
            this.cachedBuildHealthReportsBuildNumber = Integer.valueOf(lastBuild.getNumber());
            this.cachedBuildHealthReports = new ArrayList(reports);
        }
        return reports;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v27, types: [hudson.model.Run] */
    private HealthReport getBuildStabilityHealthReport() {
        Localizable description;
        int failCount = 0;
        int totalCount = 0;
        RunT i = mo6getLastBuild();
        RunT u = getLastFailedBuild();
        if (i != null && u == null) {
            return new HealthReport(100, Messages._Job_BuildStability(Messages._Job_NoRecentBuildFailed()));
        }
        if (i != null && u.getNumber() <= i.getNumber()) {
            RunMap<RunT> mo7_getRuns = mo7_getRuns();
            if (mo7_getRuns instanceof RunMap) {
                RunMap<RunT> runMap = mo7_getRuns;
                for (int index = i.getNumber(); index > u.getNumber() && totalCount < 5; index--) {
                    if (runMap.runExists(index)) {
                        totalCount++;
                    }
                }
                if (totalCount < 5) {
                    i = u;
                }
            }
        }
        while (totalCount < 5 && i != null) {
            switch (AnonymousClass5.$SwitchMap$hudson$model$BallColor[i.getIconColor().ordinal()]) {
                case Maven.MavenInstallation.MAVEN_21 /* 1 */:
                case Maven.MavenInstallation.MAVEN_30 /* 2 */:
                    totalCount++;
                    break;
                case 3:
                    failCount++;
                    totalCount++;
                    break;
            }
            i = i.getPreviousBuild();
        }
        if (totalCount > 0) {
            int score = (int) ((100.0d * (totalCount - failCount)) / totalCount);
            if (failCount == 0) {
                description = Messages._Job_NoRecentBuildFailed();
            } else if (totalCount == failCount) {
                description = Messages._Job_AllRecentBuildFailed();
            } else {
                description = Messages._Job_NOfMFailed(Integer.valueOf(failCount), Integer.valueOf(totalCount));
            }
            return new HealthReport(score, Messages._Job_BuildStability(description));
        }
        return null;
    }

    /* renamed from: hudson.model.Job$5, reason: invalid class name */
    /* loaded from: Job$5.class */
    static /* synthetic */ class AnonymousClass5 {
        static final /* synthetic */ int[] $SwitchMap$hudson$model$BallColor = new int[BallColor.values().length];

        static {
            try {
                $SwitchMap$hudson$model$BallColor[BallColor.BLUE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$hudson$model$BallColor[BallColor.YELLOW.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$hudson$model$BallColor[BallColor.RED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    @POST
    public synchronized void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);
        this.description = req.getParameter("description");
        JSONObject json = req.getSubmittedForm();
        try {
            BulkChange bc = new BulkChange(this);
            try {
                setDisplayName(json.optString("displayNameOrNull"));
                this.logRotator = null;
                DescribableList<JobProperty<?>, JobPropertyDescriptor> t = new DescribableList<>(NOOP, getAllProperties());
                JSONObject jsonProperties = json.optJSONObject("properties");
                if (jsonProperties != null) {
                    t.rebuild(req, jsonProperties, JobPropertyDescriptor.getPropertyDescriptors(getClass()));
                } else {
                    t.clear();
                }
                this.properties.clear();
                Iterator it = t.iterator();
                while (it.hasNext()) {
                    JobProperty p = (JobProperty) it.next();
                    p.setOwner(this);
                    this.properties.add(p);
                }
                submit(req, rsp);
                bc.commit();
                bc.close();
                ItemListener.fireOnUpdated(this);
                ProjectNamingStrategy namingStrategy = Jenkins.get().getProjectNamingStrategy();
                if (namingStrategy.isForceExistingJobs()) {
                    namingStrategy.checkName(m3getParent().getFullName(), this.name);
                }
                FormApply.success(".").generateResponse(req, rsp, (Object) null);
            } finally {
            }
        } catch (JSONException e) {
            LOGGER.log(Level.WARNING, "failed to parse " + String.valueOf(json), (Throwable) e);
            sendError(e, req, rsp);
        }
    }

    protected void submit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        if (Util.isOverridden(Job.class, getClass(), "submit", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
    }

    @Deprecated
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException, Descriptor.FormException {
    }

    public void doDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.getMethod().equals("GET")) {
            rsp.setContentType("text/plain;charset=UTF-8");
            rsp.getWriter().write(Util.fixNull(getDescription()));
            return;
        }
        if (req.getMethod().equals("POST")) {
            checkPermission(CONFIGURE);
            if (req.getParameter("description") != null) {
                setDescription(req.getParameter("description"));
                rsp.sendError(204);
                return;
            }
        }
        rsp.sendError(400);
    }

    public void doBuildStatus(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        rsp.sendRedirect2(req.getContextPath() + "/images/48x48/" + getBuildStatusUrl());
    }

    public String getBuildStatusUrl() {
        return getIconColor().getImage();
    }

    public String getBuildStatusIconClassName() {
        return getIconColor().getIconClassName();
    }

    /* loaded from: Job$ChartLabel.class */
    private static class ChartLabel implements Comparable<ChartLabel> {
        final Run run;

        ChartLabel(Run r) {
            this.run = r;
        }

        @Override // java.lang.Comparable
        public int compareTo(ChartLabel that) {
            return this.run.number - that.run.number;
        }

        public boolean equals(Object o) {
            if (o == null || !ChartLabel.class.isAssignableFrom(o.getClass())) {
                return false;
            }
            ChartLabel that = (ChartLabel) o;
            return this.run == that.run;
        }

        public Color getColor() {
            Result r = this.run.getResult();
            if (r == Result.FAILURE) {
                return ColorPalette.RED;
            }
            if (r == Result.UNSTABLE) {
                return ColorPalette.YELLOW;
            }
            if (r == Result.ABORTED || r == Result.NOT_BUILT) {
                return ColorPalette.DARK_GREY;
            }
            return ColorPalette.BLUE;
        }

        public int hashCode() {
            return this.run.hashCode();
        }

        public String toString() {
            String s;
            String l = this.run.getDisplayName();
            if ((this.run instanceof Build) && (s = this.run.getBuiltOnStr()) != null) {
                l = l + " " + s;
            }
            return l;
        }
    }

    @SuppressFBWarnings(value = {"EQ_DOESNT_OVERRIDE_EQUALS"}, justification = "category dataset is only relevant for coloring, not equality")
    /* loaded from: Job$ChartLabelStackedAreaRenderer2.class */
    private static class ChartLabelStackedAreaRenderer2 extends StackedAreaRenderer2 {
        private final CategoryDataset categoryDataset;

        ChartLabelStackedAreaRenderer2(CategoryDataset categoryDataset) {
            this.categoryDataset = categoryDataset;
        }

        public Paint getItemPaint(int row, int column) {
            ChartLabel key = (ChartLabel) this.categoryDataset.getColumnKey(column);
            return key.getColor();
        }

        public String generateURL(CategoryDataset dataset, int row, int column) {
            ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
            return String.valueOf(label.run.number);
        }

        public String generateToolTip(CategoryDataset dataset, int row, int column) {
            ChartLabel label = (ChartLabel) dataset.getColumnKey(column);
            return label.run.getDisplayName() + " : " + label.run.getDurationString();
        }
    }

    public Graph getBuildTimeGraph() {
        return new Graph(getLastBuildTime(), 500, 400) { // from class: hudson.model.Job.4
            protected JFreeChart createGraph() {
                DataSetBuilder<String, ChartLabel> data = new DataSetBuilder<>();
                Iterator it = Job.this.getNewBuilds().iterator();
                while (it.hasNext()) {
                    Run r = (Run) it.next();
                    if (!r.isBuilding()) {
                        data.add(Double.valueOf(r.getDuration() / 60000.0d), "min", new ChartLabel(r));
                    }
                }
                CategoryDataset dataset = data.build();
                JFreeChart chart = ChartFactory.createStackedAreaChart((String) null, (String) null, Messages.Job_minutes(), dataset, PlotOrientation.VERTICAL, false, true, false);
                chart.setBackgroundPaint(Color.white);
                CategoryPlot plot = chart.getCategoryPlot();
                plot.setBackgroundPaint(Color.WHITE);
                plot.setOutlinePaint((Paint) null);
                plot.setForegroundAlpha(0.8f);
                plot.setRangeGridlinesVisible(true);
                plot.setRangeGridlinePaint(Color.black);
                ShiftedCategoryAxis shiftedCategoryAxis = new ShiftedCategoryAxis((String) null);
                plot.setDomainAxis(shiftedCategoryAxis);
                shiftedCategoryAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
                shiftedCategoryAxis.setLowerMargin(0.0d);
                shiftedCategoryAxis.setUpperMargin(0.0d);
                shiftedCategoryAxis.setCategoryMargin(0.0d);
                NumberAxis rangeAxis = plot.getRangeAxis();
                ChartUtil.adjustChebyshev(dataset, rangeAxis);
                rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
                plot.setRenderer(new ChartLabelStackedAreaRenderer2(dataset));
                plot.setInsets(new RectangleInsets(0.0d, 0.0d, 0.0d, 5.0d));
                return chart;
            }
        };
    }

    private Calendar getLastBuildTime() {
        RunT lastBuild = mo6getLastBuild();
        if (lastBuild == null) {
            GregorianCalendar neverBuiltCalendar = new GregorianCalendar();
            neverBuiltCalendar.setTimeInMillis(0L);
            return neverBuiltCalendar;
        }
        return lastBuild.getTimestamp();
    }

    @RequirePOST
    @Deprecated
    public void doDoRename(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        String newName = req.getParameter("newName");
        doConfirmRename(newName).generateResponse(req, rsp, (Object) null);
    }

    @Override // hudson.model.AbstractItem
    protected void checkRename(String newName) throws Failure {
        if (isBuilding()) {
            throw new Failure(Messages.Job_NoRenameWhileBuilding());
        }
    }

    public void doRssAll(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (all builds)", getUrl(), m28getBuilds().newBuilds());
    }

    public void doRssFailed(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (failed builds)", getUrl(), m28getBuilds().failureOnly().newBuilds());
    }

    @Override // hudson.model.AbstractItem
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }

    @Restricted({DoNotUse.class})
    @Deprecated
    public BuildTimelineWidget getTimeline() {
        return new BuildTimelineWidget(m28getBuilds());
    }

    @Extension
    /* loaded from: Job$BasicJobDetailFactory.class */
    public static final class BasicJobDetailFactory extends DetailFactory<Job> {
        public Class<Job> type() {
            return Job.class;
        }

        @NonNull
        public List<? extends Detail> createFor(@NonNull Job target) {
            return Stream.of((Object[]) new Detail[]{new UpstreamProjectsDetail(target), new DownstreamProjectsDetail(target), new ProjectNameDetail(target)}).filter(e -> {
                return e.getIconClassName() != null;
            }).toList();
        }
    }

    @Restricted({NoExternalUse.class})
    public List<Tab> getJobTabs() {
        return getActions(Tab.class).stream().filter(e -> {
            return e.getIconFileName() != null;
        }).toList();
    }

    @Restricted({NoExternalUse.class})
    public final ParametersDefinitionProperty getParametersDefinitionProperty() {
        return getProperty(ParametersDefinitionProperty.class);
    }
}
