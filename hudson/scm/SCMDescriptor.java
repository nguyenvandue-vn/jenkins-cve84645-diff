package hudson.scm;

import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.scm.SCM;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/* loaded from: SCMDescriptor.class */
public abstract class SCMDescriptor<T extends SCM> extends Descriptor<SCM> {
    public final transient Class<? extends RepositoryBrowser> repositoryBrowser;
    private final transient AtomicInteger atomicGeneration;
    private static final Logger LOGGER = Logger.getLogger(SCMDescriptor.class.getName());

    protected SCMDescriptor(Class<T> clazz, Class<? extends RepositoryBrowser> repositoryBrowser) {
        super(clazz);
        this.atomicGeneration = new AtomicInteger(1);
        this.repositoryBrowser = repositoryBrowser;
    }

    protected SCMDescriptor(Class<? extends RepositoryBrowser> repositoryBrowser) {
        this.atomicGeneration = new AtomicInteger(1);
        this.repositoryBrowser = repositoryBrowser;
    }

    @Restricted({NoExternalUse.class})
    @RestrictedSince("2.209")
    @Deprecated
    public int getGeneration() {
        return this.atomicGeneration.get();
    }

    @Restricted({NoExternalUse.class})
    @RestrictedSince("2.209")
    @Deprecated
    public void incrementGeneration() {
        this.atomicGeneration.incrementAndGet();
    }

    public void load() {
        Class<? extends RepositoryBrowser> rb = this.repositoryBrowser;
        super.load();
        if (this.repositoryBrowser != rb) {
            try {
                Field f = SCMDescriptor.class.getDeclaredField("repositoryBrowser");
                f.setAccessible(true);
                f.set(this, rb);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                LOGGER.log(Level.WARNING, "Failed to overwrite the repositoryBrowser field", (Throwable) e);
            }
        }
    }

    @Deprecated
    public boolean isBrowserReusable(T x, T y) {
        return false;
    }

    public boolean isApplicable(Job project) {
        if (project instanceof AbstractProject) {
            return isApplicable((AbstractProject) project);
        }
        return false;
    }

    @Deprecated
    public boolean isApplicable(AbstractProject project) {
        if (Util.isOverridden(SCMDescriptor.class, getClass(), "isApplicable", new Class[]{Job.class})) {
            return isApplicable((Job) project);
        }
        return true;
    }

    public List<Descriptor<RepositoryBrowser<?>>> getBrowserDescriptors() {
        return this.repositoryBrowser == null ? Collections.emptyList() : RepositoryBrowsers.filter(this.repositoryBrowser);
    }
}
