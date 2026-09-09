package hudson.tasks;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import hudson.util.PackedMap;
import hudson.util.RunList;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.DependencyDeclarer;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.security.XStreamNotDeserializable;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.access.AccessDeniedException;

/* loaded from: Fingerprinter.class */
public class Fingerprinter extends Recorder implements Serializable, DependencyDeclarer, SimpleBuildStep {
    private final String targets;
    private String excludes;
    private Boolean defaultExcludes;
    private Boolean caseSensitive;

    @Deprecated
    Boolean recordBuildArtifacts;
    private static final long serialVersionUID = 1;

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "Accessible via System Groovy Scripts")
    public static boolean enableFingerprintsInDependencyGraph = SystemProperties.getBoolean(Fingerprinter.class.getName() + ".enableFingerprintsInDependencyGraph");
    private static final Logger logger = Logger.getLogger(Fingerprinter.class.getName());

    @DataBoundConstructor
    public Fingerprinter(String targets) {
        this.excludes = null;
        this.defaultExcludes = true;
        this.caseSensitive = true;
        this.targets = targets;
    }

    @DataBoundSetter
    public void setExcludes(String excludes) {
        this.excludes = Util.fixEmpty(excludes);
    }

    @DataBoundSetter
    public void setDefaultExcludes(boolean defaultExcludes) {
        this.defaultExcludes = Boolean.valueOf(defaultExcludes);
    }

    @DataBoundSetter
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = Boolean.valueOf(caseSensitive);
    }

    @Deprecated
    public Fingerprinter(String targets, boolean recordBuildArtifacts) {
        this(targets);
        this.recordBuildArtifacts = Boolean.valueOf(recordBuildArtifacts);
    }

    public String getTargets() {
        return this.targets;
    }

    public String getExcludes() {
        return this.excludes;
    }

    public boolean getDefaultExcludes() {
        return this.defaultExcludes.booleanValue();
    }

    public boolean getCaseSensitive() {
        return this.caseSensitive.booleanValue();
    }

    private Object readResolve() {
        if (this.defaultExcludes == null) {
            this.defaultExcludes = true;
        }
        if (this.caseSensitive == null) {
            this.caseSensitive = true;
        }
        return this;
    }

    @Deprecated
    public boolean getRecordBuildArtifacts() {
        return this.recordBuildArtifacts != null && this.recordBuildArtifacts.booleanValue();
    }

    public void perform(Run<?, ?> build, FilePath workspace, EnvVars environment, Launcher launcher, TaskListener listener) throws InterruptedException {
        try {
            listener.getLogger().println(Messages.Fingerprinter_Recording());
            Map<String, String> record = new HashMap<>();
            if (!this.targets.isEmpty()) {
                String expandedTargets = this.targets;
                if (build instanceof AbstractBuild) {
                    expandedTargets = environment.expand(expandedTargets);
                }
                record(build, workspace, listener, record, expandedTargets);
            }
            FingerprintAction fingerprintAction = build.getAction(FingerprintAction.class);
            if (fingerprintAction != null) {
                fingerprintAction.add(record);
            } else {
                build.addAction(new FingerprintAction(build, record));
            }
            if (enableFingerprintsInDependencyGraph) {
                Jenkins.get().rebuildDependencyGraphAsync();
            }
        } catch (IOException e) {
            Functions.printStackTrace(e, listener.error(Messages.Fingerprinter_Failed()));
            build.setResult(Result.FAILURE);
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        if (enableFingerprintsInDependencyGraph) {
            RunList builds = owner.m28getBuilds();
            Set<String> seenUpstreamProjects = new HashSet<>();
            Iterator it = builds.iterator();
            while (it.hasNext()) {
                Object build1 = it.next();
                Run build = (Run) build1;
                for (FingerprintAction action : build.getActions(FingerprintAction.class)) {
                    for (AbstractProject key : action.getDependencies().keySet()) {
                        if (key != owner) {
                            AbstractProject p = key;
                            if (key.getClass().getName().equals("hudson.matrix.MatrixConfiguration")) {
                                p = key.getRootProject();
                            }
                            if (!seenUpstreamProjects.contains(p.getName())) {
                                seenUpstreamProjects.add(p.getName());
                                graph.addDependency(new DependencyGraph.Dependency(this, p, owner) { // from class: hudson.tasks.Fingerprinter.1
                                    public boolean shouldTriggerBuild(AbstractBuild build2, TaskListener listener, List<Action> actions) {
                                        return false;
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    /* loaded from: Fingerprinter$Record.class */
    private static final class Record implements Serializable {
        final boolean produced;
        final String relativePath;
        final String fileName;
        final String md5sum;
        private static final long serialVersionUID = 1;

        Record(boolean produced, String relativePath, String fileName, String md5sum) {
            this.produced = produced;
            this.relativePath = relativePath;
            this.fileName = fileName;
            this.md5sum = md5sum;
        }

        Fingerprint addRecord(Run build) throws IOException {
            FingerprintMap map = Jenkins.get().getFingerprintMap();
            return map.getOrCreate(this.produced ? build : null, this.fileName, this.md5sum);
        }
    }

    /* loaded from: Fingerprinter$FindRecords.class */
    private static final class FindRecords extends MasterToSlaveFileCallable<List<Record>> {
        private final String targets;
        private final String excludes;
        private final boolean defaultExcludes;
        private final boolean caseSensitive;
        private final long buildTimestamp;

        FindRecords(String targets, String excludes, boolean defaultExcludes, boolean caseSensitive, long buildTimestamp) {
            this.targets = targets;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.caseSensitive = caseSensitive;
            this.buildTimestamp = buildTimestamp;
        }

        /* renamed from: invoke, reason: merged with bridge method [inline-methods] */
        public List<Record> m85invoke(File baseDir, VirtualChannel channel) throws IOException {
            List<Record> results = new ArrayList<>();
            FileSet src = Util.createFileSet(baseDir, this.targets, this.excludes);
            src.setDefaultexcludes(this.defaultExcludes);
            src.setCaseSensitive(this.caseSensitive);
            DirectoryScanner ds = src.getDirectoryScanner();
            for (String f : ds.getIncludedFiles()) {
                File file = new File(baseDir, f);
                boolean produced = this.buildTimestamp <= file.lastModified() + 2000;
                try {
                    results.add(new Record(produced, f, file.getName(), new FilePath(file).digest()));
                } catch (IOException e) {
                    throw new IOException(Messages.Fingerprinter_DigestFailed(file), e);
                } catch (InterruptedException e2) {
                    throw new IOException(Messages.Fingerprinter_Aborted(), e2);
                }
            }
            return results;
        }
    }

    private void record(Run<?, ?> build, FilePath ws, TaskListener listener, Map<String, String> record, final String targets) throws IOException, InterruptedException {
        for (Record r : (List) ws.act(new FindRecords(targets, this.excludes, this.defaultExcludes.booleanValue(), this.caseSensitive.booleanValue(), build.getTimeInMillis()))) {
            Fingerprint fp = r.addRecord(build);
            fp.addFor(build);
            record.put(r.relativePath, fp.getHashString());
        }
    }

    @Extension
    @Symbol({"fingerprint"})
    /* loaded from: Fingerprinter$DescriptorImpl.class */
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @NonNull
        public String getDisplayName() {
            return Messages.Fingerprinter_DisplayName();
        }

        @Deprecated
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return doCheckTargets(project, value);
        }

        public FormValidation doCheckTargets(@AncestorInPath AbstractProject<?, ?> project, @QueryParameter String value) throws IOException {
            if (project == null) {
                return FormValidation.ok();
            }
            return FilePath.validateFileMask(project.getSomeWorkspace(), value);
        }

        /* renamed from: newInstance, reason: merged with bridge method [inline-methods] */
        public Publisher m84newInstance(StaplerRequest2 req, JSONObject formData) {
            return (Publisher) req.bindJSON(Fingerprinter.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    /* loaded from: Fingerprinter$FingerprintAction.class */
    public static final class FingerprintAction implements RunAction2 {

        @XStreamNotDeserializable
        private transient Run build;
        private PackedMap<String, String> record;
        private transient WeakReference<Map<String, Fingerprint>> ref;

        public FingerprintAction(Run build, Map<String, String> record) {
            this.build = build;
            this.record = compact(record);
        }

        @Deprecated
        public FingerprintAction(AbstractBuild build, Map<String, String> record) {
            this((Run) build, record);
        }

        public void add(Map<String, String> moreRecords) {
            Map<String, String> r = new HashMap<>((Map<? extends String, ? extends String>) this.record);
            r.putAll(moreRecords);
            this.record = compact(r);
            synchronized (this) {
                this.ref = null;
            }
        }

        public String getIconFileName() {
            return "fingerprint.png";
        }

        public String getDisplayName() {
            return Messages.Fingerprinter_Action_DisplayName();
        }

        public String getUrlName() {
            return "fingerprints";
        }

        public Run getRun() {
            return this.build;
        }

        @Deprecated
        public AbstractBuild getBuild() {
            if (this.build instanceof AbstractBuild) {
                return this.build;
            }
            return null;
        }

        public Map<String, String> getRecords() {
            return this.record;
        }

        public void onLoad(Run<?, ?> r) {
            this.build = r;
            this.record = compact(this.record);
        }

        public void onAttached(Run<?, ?> r) {
        }

        private PackedMap<String, String> compact(Map<String, String> record) {
            Map<String, String> b = new HashMap<>();
            for (Map.Entry<String, String> e : record.entrySet()) {
                b.put(e.getKey().intern(), e.getValue().intern());
            }
            return PackedMap.of(b);
        }

        public synchronized Map<String, Fingerprint> getFingerprints() {
            Map<String, Fingerprint> m;
            if (this.ref != null && (m = this.ref.get()) != null) {
                return m;
            }
            Jenkins h = Jenkins.get();
            Map<String, Fingerprint> m2 = new TreeMap<>();
            for (Map.Entry<String, String> r : this.record.entrySet()) {
                try {
                    Fingerprint fp = h._getFingerprint(r.getValue());
                    if (fp != null) {
                        m2.put(r.getKey(), fp);
                    }
                } catch (IOException e) {
                    Fingerprinter.logger.log(Level.WARNING, e.getMessage(), (Throwable) e);
                }
            }
            Map<String, Fingerprint> m3 = Collections.unmodifiableMap(m2);
            this.ref = new WeakReference<>(m3);
            return m3;
        }

        public Map<AbstractProject, Integer> getDependencies() {
            return getDependencies(false);
        }

        @SuppressFBWarnings(value = {"EC_UNRELATED_TYPES_USING_POINTER_EQUALITY"}, justification = "TODO needs triage")
        public Map<AbstractProject, Integer> getDependencies(boolean includeMissing) {
            Map<AbstractProject, Integer> r = new HashMap<>();
            for (Fingerprint fp : getFingerprints().values()) {
                Fingerprint.BuildPtr bp = fp.getOriginal();
                if (bp != null && !bp.is(this.build)) {
                    try {
                        Job job = bp.getJob();
                        if (job != null && (job instanceof AbstractProject) && job.m3getParent() != this.build.getParent() && (includeMissing || job.mo10getBuildByNumber(bp.getNumber()) != null)) {
                            Integer existing = r.get(job);
                            if (existing == null || existing.intValue() <= bp.getNumber()) {
                                r.put((AbstractProject) job, Integer.valueOf(bp.getNumber()));
                            }
                        }
                    } catch (AccessDeniedException e) {
                    }
                }
            }
            return r;
        }
    }
}
