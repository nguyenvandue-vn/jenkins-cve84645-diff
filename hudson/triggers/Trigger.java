package hudson.triggers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DependencyRunner;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.PeriodicWork;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.scheduler.CronTabList;
import hudson.scheduler.Hash;
import hudson.triggers.SCMTrigger;
import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.triggers.TriggeredItem;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/* loaded from: Trigger.class */
public abstract class Trigger<J extends Item> implements Describable<Trigger<?>>, ExtensionPoint {
    protected final String spec;
    protected transient CronTabList tabs;

    @CheckForNull
    protected transient J job;
    private static Future previousSynchronousPolling;

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    @RestrictedSince("2.289")
    public static long CRON_THRESHOLD = SystemProperties.getLong(Trigger.class.getName() + ".CRON_THRESHOLD", 30L).longValue();
    private static final Logger LOGGER = Logger.getLogger(Trigger.class.getName());

    @CheckForNull
    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL"}, justification = "for backward compatibility")
    @Deprecated
    public static Timer timer;

    public void start(J project, boolean newInstance) {
        LOGGER.finer(() -> {
            return "Starting " + String.valueOf(this) + " on " + String.valueOf(project);
        });
        this.job = project;
        try {
            if (this.spec != null) {
                this.tabs = CronTabList.create(this.spec, Hash.from(project.getFullName()));
            } else {
                LOGGER.log(Level.WARNING, "The job {0} has a null crontab spec which is incorrect", this.job.getFullName());
            }
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to parse crontab spec %s in job %s", this.spec, project.getFullName()), (Throwable) e);
        }
    }

    public void run() {
    }

    public void stop() {
    }

    @Deprecated
    public Action getProjectAction() {
        return null;
    }

    public Collection<? extends Action> getProjectActions() {
        Action a = getProjectAction();
        return a == null ? Collections.emptyList() : List.of(a);
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // 
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public TriggerDescriptor mo96getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    protected Trigger(@NonNull String cronTabSpec) {
        this.spec = cronTabSpec;
        this.tabs = CronTabList.create(cronTabSpec);
    }

    protected Trigger() {
        this.spec = "";
        this.tabs = new CronTabList(Collections.emptyList());
    }

    public final String getSpec() {
        return this.spec;
    }

    protected Object readResolve() throws ObjectStreamException {
        try {
            this.tabs = CronTabList.create(this.spec);
            return this;
        } catch (IllegalArgumentException e) {
            InvalidObjectException x = new InvalidObjectException(e.getMessage());
            x.initCause(e);
            throw x;
        }
    }

    public String toString() {
        return super.toString() + "[" + this.spec + "]";
    }

    @Extension
    @Symbol({"cron"})
    /* loaded from: Trigger$Cron.class */
    public static class Cron extends PeriodicWork {
        private final Calendar cal = new GregorianCalendar();

        public Cron() {
            this.cal.set(13, 0);
            this.cal.set(14, 0);
        }

        public long getRecurrencePeriod() {
            return 60000L;
        }

        public long getInitialDelay() {
            return 60000 - TimeUnit.SECONDS.toMillis(Calendar.getInstance().get(13));
        }

        public void doRun() {
            while (new Date().getTime() >= this.cal.getTimeInMillis()) {
                Trigger.LOGGER.log(Level.FINE, "cron checking {0}", this.cal.getTime());
                try {
                    Trigger.checkTriggers(this.cal);
                } catch (Throwable e) {
                    Trigger.LOGGER.log(Level.WARNING, "Cron thread throw an exception", e);
                }
                this.cal.add(12, 1);
            }
        }
    }

    @SuppressFBWarnings(value = {"LI_LAZY_INIT_STATIC"}, justification = "TODO needs triage")
    public static void checkTriggers(final Calendar cal) {
        Jenkins inst = Jenkins.get();
        SCMTrigger.DescriptorImpl scmd = inst.getDescriptorByType(SCMTrigger.DescriptorImpl.class);
        if (scmd.synchronousPolling) {
            LOGGER.fine("using synchronous polling");
            if (previousSynchronousPolling == null || previousSynchronousPolling.isDone()) {
                previousSynchronousPolling = scmd.getExecutor().submit((Runnable) new DependencyRunner(p -> {
                    for (Trigger t : p.getTriggers().values()) {
                        if (t instanceof SCMTrigger) {
                            if (t.job != null) {
                                LOGGER.fine("synchronously triggering SCMTrigger for project " + t.job.getName());
                            } else {
                                LOGGER.fine("synchronously triggering SCMTrigger for unknown project");
                            }
                            t.run();
                        }
                    }
                }));
            } else {
                LOGGER.fine("synchronous polling has detected unfinished jobs, will not trigger additional jobs.");
            }
        }
        for (TriggeredItem p2 : inst.allItems(TriggeredItem.class)) {
            LOGGER.finer(() -> {
                return "considering " + String.valueOf(p2);
            });
            for (Trigger t : p2.getTriggers().values()) {
                LOGGER.finer(() -> {
                    return "found trigger " + String.valueOf(t);
                });
                if (!(p2 instanceof AbstractProject) || !(t instanceof SCMTrigger) || !scmd.synchronousPolling) {
                    if (t != null && t.spec != null && t.tabs != null) {
                        LOGGER.log(Level.FINE, "cron checking {0} with spec ‘{1}’", new Object[]{p2, t.spec.trim()});
                        if (t.tabs.check(cal)) {
                            LOGGER.log(Level.CONFIG, "cron triggered {0}", p2);
                            try {
                                long begin_time = System.currentTimeMillis();
                                if (t.job == null) {
                                    LOGGER.fine(() -> {
                                        return String.valueOf(t) + " not yet started on " + String.valueOf(p2) + " but trying to run anyway";
                                    });
                                }
                                t.run();
                                long end_time = System.currentTimeMillis();
                                if (end_time - begin_time > CRON_THRESHOLD * 1000) {
                                    TriggerDescriptor descriptor = t.mo96getDescriptor();
                                    String name = descriptor.getDisplayName();
                                    String msg = String.format("Trigger '%s' triggered by '%s' (%s) spent too much time (%s) in its execution, other timers could be delayed.", name, p2.getFullDisplayName(), p2.getFullName(), Util.getTimeSpanString(end_time - begin_time));
                                    LOGGER.log(Level.WARNING, msg);
                                    SlowTriggerAdminMonitor.getInstance().report(descriptor.getClass(), p2.getFullName(), end_time - begin_time);
                                }
                            } catch (Throwable e) {
                                LOGGER.log(Level.WARNING, t.getClass().getName() + ".run() failed for " + String.valueOf(p2), e);
                            }
                        } else {
                            LOGGER.log(Level.FINER, "did not trigger {0}", p2);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "The job {0} has a syntactically incorrect config and is missing the cron spec for a trigger", p2.getFullName());
                    }
                }
            }
        }
    }

    public static DescriptorExtensionList<Trigger<?>, TriggerDescriptor> all() {
        return Jenkins.get().getDescriptorList(Trigger.class);
    }

    public static List<TriggerDescriptor> for_(Item i) {
        TopLevelItemDescriptor tld;
        List<TriggerDescriptor> r = new ArrayList<>();
        Iterator it = all().iterator();
        while (it.hasNext()) {
            TriggerDescriptor t = (TriggerDescriptor) it.next();
            if (t.isApplicable(i) && (!(i instanceof TopLevelItem) || (tld = ((TopLevelItem) i).getDescriptor()) == null || tld.isApplicable(t))) {
                r.add(t);
            }
        }
        return r;
    }
}
