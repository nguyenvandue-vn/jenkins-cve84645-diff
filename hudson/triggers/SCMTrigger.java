package hudson.triggers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AdministrativeMonitor;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.PersistentDescriptor;
import hudson.model.Run;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.triggers.TimerTrigger;
import hudson.util.DaemonThreadFactory;
import hudson.util.FlushProofOutputStream;
import hudson.util.FormValidation;
import hudson.util.NamingThreadFactory;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import jenkins.scm.SCMDecisionHandler;
import jenkins.triggers.SCMTriggerItem;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.apache.commons.jelly.XMLOutput;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/* loaded from: SCMTrigger.class */
public class SCMTrigger extends Trigger<Item> {
    private boolean ignorePostCommitHooks;
    private static final Logger LOGGER = Logger.getLogger(SCMTrigger.class.getName());

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static long STARVATION_THRESHOLD = SystemProperties.getLong(SCMTrigger.class.getName() + ".starvationThreshold", Long.valueOf(TimeUnit.HOURS.toMillis(1))).longValue();

    @DataBoundConstructor
    public SCMTrigger(String scmpoll_spec) {
        super(scmpoll_spec);
    }

    @Deprecated
    public SCMTrigger(String scmpoll_spec, boolean ignorePostCommitHooks) {
        super(scmpoll_spec);
        this.ignorePostCommitHooks = ignorePostCommitHooks;
    }

    public boolean isIgnorePostCommitHooks() {
        return this.ignorePostCommitHooks;
    }

    @DataBoundSetter
    public void setIgnorePostCommitHooks(boolean ignorePostCommitHooks) {
        this.ignorePostCommitHooks = ignorePostCommitHooks;
    }

    public String getScmpoll_spec() {
        return super.getSpec();
    }

    @Override // hudson.triggers.Trigger
    public void run() {
        if (this.job == 0) {
            return;
        }
        run(null);
    }

    public void run(Action[] additionalActions) {
        if (this.job == 0) {
            return;
        }
        DescriptorImpl d = mo96getDescriptor();
        LOGGER.fine("Scheduling a polling for " + String.valueOf(this.job));
        if (d.synchronousPolling) {
            LOGGER.fine("Running the trigger directly without threading, as it's already taken care of by Trigger.Cron");
            new Runner(additionalActions).run();
        } else {
            LOGGER.fine("scheduling the trigger to (asynchronously) run");
            d.queue.execute(new Runner(additionalActions));
            d.clogCheck();
        }
    }

    @Override // hudson.triggers.Trigger
    /* renamed from: getDescriptor */
    public DescriptorImpl mo96getDescriptor() {
        return (DescriptorImpl) super.mo96getDescriptor();
    }

    @Override // hudson.triggers.Trigger
    public Collection<? extends Action> getProjectActions() {
        if (this.job == 0) {
            return Collections.emptyList();
        }
        return Set.of(new SCMAction());
    }

    public File getLogFile() {
        return new File(((Item) Objects.requireNonNull(this.job)).getRootDir(), "scm-polling.log");
    }

    @Extension
    @Symbol({"pollSCM"})
    /* loaded from: SCMTrigger$DescriptorImpl.class */
    public static class DescriptorImpl extends TriggerDescriptor implements PersistentDescriptor {
        private final transient SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor(threadFactory()));
        public boolean synchronousPolling = false;
        private int maximumThreads = THREADS_DEFAULT;
        private static final int THREADS_LOWER_BOUND = 5;
        private static final int THREADS_UPPER_BOUND = 100;
        private static final int THREADS_DEFAULT = 10;

        private static ThreadFactory threadFactory() {
            return new NamingThreadFactory(new DaemonThreadFactory(), "SCMTrigger");
        }

        private Object readResolve() {
            if (this.maximumThreads == 0) {
                this.maximumThreads = THREADS_DEFAULT;
            }
            return this;
        }

        public boolean isApplicable(Item item) {
            return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item) != null;
        }

        public ExecutorService getExecutor() {
            return this.queue.getExecutors();
        }

        public boolean isClogged() {
            return this.queue.isStarving(SCMTrigger.STARVATION_THRESHOLD);
        }

        public void clogCheck() {
            ((AdministrativeMonitorImpl) AdministrativeMonitor.all().get(AdministrativeMonitorImpl.class)).on = isClogged();
        }

        public List<Runner> getRunners() {
            return Util.filter(this.queue.getInProgress(), Runner.class);
        }

        public List<SCMTriggerItem> getItemsBeingPolled() {
            List<SCMTriggerItem> r = new ArrayList<>();
            for (Runner i : getRunners()) {
                r.add(i.getTarget());
            }
            return r;
        }

        @NonNull
        public String getDisplayName() {
            return Messages.SCMTrigger_DisplayName();
        }

        public int getPollingThreadCount() {
            return this.maximumThreads;
        }

        public void setPollingThreadCount(int n) {
            if (n < THREADS_LOWER_BOUND) {
                n = THREADS_LOWER_BOUND;
            }
            if (n > THREADS_UPPER_BOUND) {
                n = THREADS_UPPER_BOUND;
            }
            this.maximumThreads = n;
            resizeThreadPool();
        }

        @Restricted({NoExternalUse.class})
        public boolean isPollingThreadCountOptionVisible() {
            if (getPollingThreadCount() != THREADS_DEFAULT) {
                return true;
            }
            int count = 0;
            for (Item item : Jenkins.get().allItems(Item.class)) {
                if (item instanceof SCMTriggerItem) {
                    count++;
                    if (count > THREADS_DEFAULT) {
                        return true;
                    }
                }
            }
            return false;
        }

        @PostConstruct
        synchronized void resizeThreadPool() {
            this.queue.setExecutors(Executors.newFixedThreadPool(this.maximumThreads, threadFactory()));
        }

        public boolean configure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException {
            String t = json.optString("pollingThreadCount", (String) null);
            if (doCheckPollingThreadCount(t).kind != FormValidation.Kind.OK) {
                setPollingThreadCount(THREADS_DEFAULT);
            } else {
                setPollingThreadCount(Integer.parseInt(t));
            }
            save();
            return true;
        }

        public FormValidation doCheckPollingThreadCount(@QueryParameter String value) {
            return FormValidation.validateIntegerInRange(value, THREADS_LOWER_BOUND, THREADS_UPPER_BOUND);
        }

        public FormValidation doCheckScmpoll_spec(@QueryParameter String value, @QueryParameter boolean ignorePostCommitHooks, @AncestorInPath Item item) {
            if (value == null || value.isBlank()) {
                if (ignorePostCommitHooks) {
                    return FormValidation.ok(Messages.SCMTrigger_no_schedules_no_hooks());
                }
                return FormValidation.ok(Messages.SCMTrigger_no_schedules_hooks());
            }
            return Jenkins.get().getDescriptorByType(TimerTrigger.DescriptorImpl.class).doCheckSpec(value, item);
        }
    }

    @Extension
    /* loaded from: SCMTrigger$AdministrativeMonitorImpl.class */
    public static final class AdministrativeMonitorImpl extends AdministrativeMonitor {
        private boolean on;

        public String getDisplayName() {
            return Messages.SCMTrigger_AdministrativeMonitorImpl_DisplayName();
        }

        public boolean isActivated() {
            return this.on;
        }
    }

    /* loaded from: SCMTrigger$BuildAction.class */
    public static class BuildAction implements RunAction2 {
        private transient Run<?, ?> run;

        @SuppressFBWarnings(value = {"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"}, justification = "for backward compatibility")
        @Deprecated
        public transient AbstractBuild build;

        public BuildAction(Run<?, ?> run) {
            this.run = run;
            this.build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
        }

        @Deprecated
        public BuildAction(AbstractBuild build) {
            this((Run<?, ?>) build);
        }

        public Run<?, ?> getRun() {
            return this.run;
        }

        public File getPollingLogFile() {
            return new File(this.run.getRootDir(), "polling.log");
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            return Messages.SCMTrigger_BuildAction_DisplayName();
        }

        public String getUrlName() {
            return "pollingLog";
        }

        public void doPollingLog(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            ServletOutputStream outputStream = rsp.getOutputStream();
            try {
                FlushProofOutputStream out = new FlushProofOutputStream(outputStream);
                try {
                    getPollingLogText().writeLogTo(0L, out);
                    out.close();
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } finally {
                }
            } catch (Throwable th) {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }

        public AnnotatedLargeText getPollingLogText() {
            return new AnnotatedLargeText(getPollingLogFile(), Charset.defaultCharset(), true, this);
        }

        @SuppressFBWarnings(value = {"RV_RETURN_VALUE_IGNORED"}, justification = "method signature does not permit plumbing through the return value")
        public void writePollingLogTo(long offset, XMLOutput out) throws IOException {
            getPollingLogText().writeHtmlTo(offset, out.asWriter());
        }

        public void onAttached(Run<?, ?> r) {
        }

        public void onLoad(Run<?, ?> r) {
            this.run = r;
            this.build = this.run instanceof AbstractBuild ? (AbstractBuild) this.run : null;
        }
    }

    /* loaded from: SCMTrigger$SCMAction.class */
    public final class SCMAction implements Action {
        public SCMAction() {
        }

        public AbstractProject<?, ?> getOwner() {
            Item item = getItem();
            if (item instanceof AbstractProject) {
                return (AbstractProject) item;
            }
            return null;
        }

        public Item getItem() {
            return SCMTrigger.this.job().asItem();
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            Set<SCMDescriptor<?>> descriptors = new HashSet<>();
            for (SCM scm : SCMTrigger.this.job().getSCMs()) {
                descriptors.add(scm.getDescriptor());
            }
            return descriptors.size() == 1 ? Messages.SCMTrigger_getDisplayName(descriptors.iterator().next().getDisplayName()) : Messages.SCMTrigger_BuildAction_DisplayName();
        }

        public String getUrlName() {
            return "scmPollLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(SCMTrigger.this.getLogFile(), Charset.defaultCharset());
        }

        @SuppressFBWarnings(value = {"RV_RETURN_VALUE_IGNORED"}, justification = "method signature does not permit plumbing through the return value")
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText(SCMTrigger.this.getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0L, out.asWriter());
        }
    }

    /* loaded from: SCMTrigger$Runner.class */
    public class Runner implements Runnable {
        private volatile long startTime;
        private Action[] additionalActions;

        public Runner(final SCMTrigger this$0) {
            this(null);
        }

        public Runner(Action[] actions) {
            Objects.requireNonNull(SCMTrigger.this.job, "Runner can't be instantiated when job is null");
            if (actions == null) {
                this.additionalActions = new Action[0];
            } else {
                this.additionalActions = (Action[]) Arrays.copyOf(actions, actions.length);
            }
        }

        public File getLogFile() {
            return SCMTrigger.this.getLogFile();
        }

        public SCMTriggerItem getTarget() {
            return SCMTrigger.this.job();
        }

        public long getStartTime() {
            return this.startTime;
        }

        public String getDuration() {
            return Util.getTimeSpanString(System.currentTimeMillis() - this.startTime);
        }

        /* JADX WARN: Failed to apply debug info
        java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.InsnArg.getType()" because "changeArg" is null
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.moveListener(TypeUpdate.java:439)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.runListeners(TypeUpdate.java:232)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.requestUpdate(TypeUpdate.java:212)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeForSsaVar(TypeUpdate.java:183)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeChecked(TypeUpdate.java:112)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:83)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.applyWithWiderIgnoreUnknown(TypeUpdate.java:74)
        	at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.applyDebugInfo(DebugInfoApplyVisitor.java:137)
        	at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.applyDebugInfo(DebugInfoApplyVisitor.java:133)
        	at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.searchAndApplyVarDebugInfo(DebugInfoApplyVisitor.java:75)
        	at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.lambda$applyDebugInfo$0(DebugInfoApplyVisitor.java:68)
        	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
        	at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.applyDebugInfo(DebugInfoApplyVisitor.java:68)
        	at jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor.visit(DebugInfoApplyVisitor.java:55)
         */
        /* JADX WARN: Failed to calculate best type for var: r7v1 ??
        java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.InsnArg.getType()" because "changeArg" is null
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.moveListener(TypeUpdate.java:439)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.runListeners(TypeUpdate.java:232)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.requestUpdate(TypeUpdate.java:212)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeForSsaVar(TypeUpdate.java:183)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeChecked(TypeUpdate.java:112)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:83)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:56)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.calculateFromBounds(FixTypesVisitor.java:156)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.setBestType(FixTypesVisitor.java:133)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.deduceType(FixTypesVisitor.java:238)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryDeduceTypes(FixTypesVisitor.java:221)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:91)
         */
        /* JADX WARN: Failed to calculate best type for var: r7v1 ??
        java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.InsnArg.getType()" because "changeArg" is null
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.moveListener(TypeUpdate.java:439)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.runListeners(TypeUpdate.java:232)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.requestUpdate(TypeUpdate.java:212)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeForSsaVar(TypeUpdate.java:183)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeChecked(TypeUpdate.java:112)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:83)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:56)
        	at jadx.core.dex.visitors.typeinference.TypeInferenceVisitor.calculateFromBounds(TypeInferenceVisitor.java:145)
        	at jadx.core.dex.visitors.typeinference.TypeInferenceVisitor.setBestType(TypeInferenceVisitor.java:123)
        	at jadx.core.dex.visitors.typeinference.TypeInferenceVisitor.lambda$runTypePropagation$2(TypeInferenceVisitor.java:101)
        	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
        	at jadx.core.dex.visitors.typeinference.TypeInferenceVisitor.runTypePropagation(TypeInferenceVisitor.java:101)
        	at jadx.core.dex.visitors.typeinference.TypeInferenceVisitor.visit(TypeInferenceVisitor.java:75)
         */
        /* JADX WARN: Multi-variable type inference failed. Error: java.lang.NullPointerException: Cannot invoke "jadx.core.dex.instructions.args.InsnArg.getType()" because "changeArg" is null
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.moveListener(TypeUpdate.java:439)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.runListeners(TypeUpdate.java:232)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.requestUpdate(TypeUpdate.java:212)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeForSsaVar(TypeUpdate.java:183)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.updateTypeChecked(TypeUpdate.java:112)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.apply(TypeUpdate.java:83)
        	at jadx.core.dex.visitors.typeinference.TypeUpdate.applyWithWiderIgnSame(TypeUpdate.java:70)
        	at jadx.core.dex.visitors.typeinference.TypeSearch.applyResolvedVars(TypeSearch.java:100)
        	at jadx.core.dex.visitors.typeinference.TypeSearch.run(TypeSearch.java:76)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.runMultiVariableSearch(FixTypesVisitor.java:116)
        	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:91)
         */
        /* JADX WARN: Not initialized variable reg: 7, insn: 0x00a5: MOVE (r0 I:??[int, float, boolean, short, byte, char, OBJECT, ARRAY]) = (r7 I:??[int, float, boolean, short, byte, char, OBJECT, ARRAY] A[D('listener' hudson.util.StreamTaskListener)]) A[TRY_LEAVE], block:B:16:0x00a5 */
        private boolean runPolling() {
            StreamTaskListener streamTaskListener;
            try {
                try {
                    streamTaskListener = new StreamTaskListener(getLogFile(), Charset.defaultCharset());
                    try {
                        PrintStream logger = streamTaskListener.getLogger();
                        long currentTimeMillis = System.currentTimeMillis();
                        logger.println("Started on " + DateFormat.getDateTimeInstance().format(new Date()));
                        boolean hasChanges = SCMTrigger.this.job().poll(streamTaskListener).hasChanges();
                        logger.println("Done. Took " + Util.getTimeSpanString(System.currentTimeMillis() - currentTimeMillis));
                        if (hasChanges) {
                            logger.println("Changes found");
                        } else {
                            logger.println("No changes");
                        }
                        return hasChanges;
                    } catch (Error | RuntimeException e) {
                        Functions.printStackTrace(e, streamTaskListener.error("Failed to record SCM polling for " + String.valueOf(SCMTrigger.this.job)));
                        SCMTrigger.LOGGER.log(Level.SEVERE, "Failed to record SCM polling for " + String.valueOf(SCMTrigger.this.job), e);
                        throw e;
                    }
                } finally {
                    streamTaskListener.close();
                }
            } catch (IOException e2) {
                SCMTrigger.LOGGER.log(Level.SEVERE, "Failed to record SCM polling for " + String.valueOf(SCMTrigger.this.job), (Throwable) e2);
                return false;
            }
        }

        @Override // java.lang.Runnable
        public void run() {
            SCMTriggerCause cause;
            if (SCMTrigger.this.job == 0) {
                return;
            }
            SCMDecisionHandler veto = SCMDecisionHandler.firstShouldPollVeto(SCMTrigger.this.job);
            if (veto != null) {
                try {
                    StreamTaskListener listener = new StreamTaskListener(getLogFile(), Charset.defaultCharset());
                    try {
                        listener.getLogger().println("Skipping polling on " + DateFormat.getDateTimeInstance().format(new Date()) + " due to veto from " + String.valueOf(veto));
                        listener.close();
                    } finally {
                    }
                } catch (IOException e) {
                    SCMTrigger.LOGGER.log(Level.SEVERE, "Failed to record SCM polling for " + String.valueOf(SCMTrigger.this.job), (Throwable) e);
                }
                SCMTrigger.LOGGER.log(Level.FINE, "Skipping polling for {0} due to veto from {1}", new Object[]{SCMTrigger.this.job.getFullDisplayName(), veto});
                return;
            }
            String threadName = Thread.currentThread().getName();
            Thread.currentThread().setName("SCM polling for " + String.valueOf(SCMTrigger.this.job));
            try {
                this.startTime = System.currentTimeMillis();
                if (runPolling()) {
                    SCMTriggerItem p = SCMTrigger.this.job();
                    String name = " #" + p.getNextBuildNumber();
                    try {
                        cause = new SCMTriggerCause(getLogFile());
                    } catch (IOException e2) {
                        SCMTrigger.LOGGER.log(Level.WARNING, "Failed to parse the polling log", (Throwable) e2);
                        cause = new SCMTriggerCause();
                    }
                    Action[] queueActions = new Action[this.additionalActions.length + 1];
                    queueActions[0] = new CauseAction(cause);
                    System.arraycopy(this.additionalActions, 0, queueActions, 1, this.additionalActions.length);
                    if (p.scheduleBuild2(p.getQuietPeriod(), queueActions) != null) {
                        SCMTrigger.LOGGER.info("SCM changes detected in " + SCMTrigger.this.job.getFullDisplayName() + ". Triggering " + name);
                    } else {
                        SCMTrigger.LOGGER.info("SCM changes detected in " + SCMTrigger.this.job.getFullDisplayName() + ". Job is already in the queue");
                    }
                }
                Thread.currentThread().setName(threadName);
            } catch (Throwable th) {
                Thread.currentThread().setName(threadName);
                throw th;
            }
        }

        public boolean equals(Object that) {
            return (that instanceof Runner) && SCMTrigger.this.job == ((Runner) that)._job();
        }

        private Item _job() {
            return SCMTrigger.this.job;
        }

        public int hashCode() {
            return ((Item) Objects.requireNonNull(SCMTrigger.this.job)).hashCode();
        }
    }

    private SCMTriggerItem job() {
        return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(this.job);
    }

    /* loaded from: SCMTrigger$SCMTriggerCause.class */
    public static class SCMTriggerCause extends Cause {

        @CheckForNull
        private String pollingLog;
        private transient Run run;

        public SCMTriggerCause(File logFile) throws IOException {
            this(Files.readString(Util.fileToPath(logFile), Charset.defaultCharset()));
        }

        public SCMTriggerCause(String pollingLog) {
            this.pollingLog = pollingLog;
        }

        @Deprecated
        public SCMTriggerCause() {
            this("");
        }

        @Override // hudson.model.Cause
        public void onLoad(Run run) {
            this.run = run;
        }

        @Override // hudson.model.Cause
        public void onAddedTo(Run build) {
            this.run = build;
            try {
                BuildAction a = new BuildAction((Run<?, ?>) build);
                if (this.pollingLog != null) {
                    Files.writeString(Util.fileToPath(a.getPollingLogFile()), this.pollingLog, Charset.defaultCharset(), new OpenOption[0]);
                }
                build.replaceAction(a);
            } catch (IOException e) {
                SCMTrigger.LOGGER.log(Level.WARNING, "Failed to persist the polling log", (Throwable) e);
            }
            this.pollingLog = null;
        }

        @Override // hudson.model.Cause
        public String getShortDescription() {
            return Messages.SCMTrigger_SCMTriggerCause_ShortDescription();
        }

        @Restricted({DoNotUse.class})
        public Run getRun() {
            return this.run;
        }

        public boolean equals(Object o) {
            return o instanceof SCMTriggerCause;
        }

        public int hashCode() {
            return 3;
        }
    }
}
