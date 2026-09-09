package hudson.model;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Functions;
import hudson.Util;
import hudson.console.ModelHyperlinkNote;
import hudson.diagnosis.OldDataMonitor;
import hudson.util.XStream2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.security.XStreamDeserializable;
import jenkins.security.XStreamNotDeserializable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
/* loaded from: Cause.class */
public abstract class Cause {
    @Exported(visibility = 3)
    public abstract String getShortDescription();

    public void onAddedTo(@NonNull Run build) {
        if (build instanceof AbstractBuild) {
            onAddedTo((AbstractBuild) build);
        }
    }

    @Deprecated
    public void onAddedTo(AbstractBuild build) {
        if (Util.isOverridden(Cause.class, getClass(), "onAddedTo", new Class[]{Run.class})) {
            onAddedTo((Run) build);
        }
    }

    public void onLoad(@NonNull Run<?, ?> build) {
        if (build instanceof AbstractBuild) {
            onLoad((AbstractBuild<?, ?>) build);
        }
    }

    void onLoad(@NonNull Job<?, ?> job, int buildNumber) {
        Run<?, ?> build = job.mo10getBuildByNumber(buildNumber);
        if (build != null) {
            onLoad(build);
        }
    }

    @Deprecated
    public void onLoad(AbstractBuild<?, ?> build) {
        if (Util.isOverridden(Cause.class, getClass(), "onLoad", new Class[]{Run.class})) {
            onLoad((Run<?, ?>) build);
        }
    }

    public void print(TaskListener listener) {
        listener.getLogger().println(getShortDescription());
    }

    @Deprecated
    /* loaded from: Cause$LegacyCodeCause.class */
    public static class LegacyCodeCause extends Cause {

        @SuppressFBWarnings(value = {"URF_UNREAD_FIELD"}, justification = "for backward compatibility")
        private StackTraceElement[] stackTrace = new Exception().getStackTrace();

        @Override // hudson.model.Cause
        public String getShortDescription() {
            return Messages.Cause_LegacyCodeCause_ShortDescription();
        }
    }

    /* loaded from: Cause$UpstreamCause.class */
    public static class UpstreamCause extends Cause {
        private static final int MAX_DEPTH = 10;
        private static final int MAX_LEAF = 25;
        private String upstreamProject;
        private String upstreamUrl;
        private int upstreamBuild;

        @XStreamDeserializable
        @Deprecated
        private transient Cause upstreamCause;

        @NonNull
        private List<Cause> upstreamCauses;

        @XStreamNotDeserializable
        private transient Map<Cause, Integer> causeBag;

        @Deprecated
        public UpstreamCause(AbstractBuild<?, ?> up) {
            this((Run<?, ?>) up);
        }

        /* JADX WARN: Type inference failed for: r1v3, types: [hudson.model.Job] */
        /* JADX WARN: Type inference failed for: r1v6, types: [hudson.model.Job] */
        public UpstreamCause(Run<?, ?> up) {
            this.upstreamBuild = up.getNumber();
            this.upstreamProject = up.getParent().getFullName();
            this.upstreamUrl = up.getParent().getUrl();
            this.upstreamCauses = new ArrayList();
            Set<String> traversed = new HashSet<>();
            for (Cause c : up.getCauses()) {
                if (traversed.size() >= MAX_LEAF) {
                    this.upstreamCauses.add(new DeeplyNestedUpstreamCause());
                    return;
                }
                this.upstreamCauses.add(trim(c, MAX_DEPTH, traversed));
            }
        }

        private UpstreamCause(String upstreamProject, int upstreamBuild, String upstreamUrl, @NonNull List<Cause> upstreamCauses) {
            this.upstreamProject = upstreamProject;
            this.upstreamBuild = upstreamBuild;
            this.upstreamUrl = upstreamUrl;
            this.upstreamCauses = upstreamCauses;
        }

        private synchronized void fillCauseBag() {
            if (this.causeBag == null) {
                this.causeBag = new LinkedHashMap();
                for (Cause c : this.upstreamCauses) {
                    this.causeBag.compute(c, (unused, cnt) -> {
                        return Integer.valueOf(cnt == null ? 1 : cnt.intValue() + 1);
                    });
                }
            }
        }

        @Restricted({DoNotUse.class})
        public Map<Cause, Integer> getCauseCounts() {
            fillCauseBag();
            return Collections.unmodifiableMap(this.causeBag);
        }

        @Override // hudson.model.Cause
        public void onLoad(@NonNull Run<?, ?> build) {
            onLoad(build.getParent(), build.getNumber());
        }

        @Override // hudson.model.Cause
        public void onLoad(@NonNull Job<?, ?> _job, int _buildNumber) {
            Item i = Jenkins.get().getItemByFullName(this.upstreamProject);
            if (!(i instanceof Job)) {
                return;
            }
            Job j = (Job) i;
            for (Cause c : this.upstreamCauses) {
                c.onLoad(j, this.upstreamBuild);
            }
        }

        public boolean equals(Object rhs) {
            if (this == rhs) {
                return true;
            }
            if (!(rhs instanceof UpstreamCause)) {
                return false;
            }
            UpstreamCause o = (UpstreamCause) rhs;
            return Objects.equals(Integer.valueOf(this.upstreamBuild), Integer.valueOf(o.upstreamBuild)) && Objects.equals(this.upstreamCauses, o.upstreamCauses) && Objects.equals(this.upstreamUrl, o.upstreamUrl) && Objects.equals(this.upstreamProject, o.upstreamProject);
        }

        public int hashCode() {
            return Objects.hash(this.upstreamCauses, Integer.valueOf(this.upstreamBuild), this.upstreamUrl, this.upstreamProject);
        }

        @NonNull
        private Cause trim(@NonNull Cause c, int depth, Set<String> traversed) {
            if (!(c instanceof UpstreamCause)) {
                return c;
            }
            UpstreamCause uc = (UpstreamCause) c;
            List<Cause> cs = new ArrayList<>();
            if (traversed.add(uc.upstreamUrl + uc.upstreamBuild)) {
                for (Cause c2 : uc.upstreamCauses) {
                    if (depth <= 0 || traversed.size() >= MAX_LEAF) {
                        cs.add(new DeeplyNestedUpstreamCause());
                        break;
                    }
                    cs.add(trim(c2, depth - 1, traversed));
                }
            } else {
                traversed.add(uc.upstreamUrl + uc.upstreamBuild + "#" + traversed.size());
            }
            return new UpstreamCause(uc.upstreamProject, uc.upstreamBuild, uc.upstreamUrl, cs);
        }

        public boolean pointsTo(Job<?, ?> j) {
            return j.getFullName().equals(this.upstreamProject);
        }

        public boolean pointsTo(Run<?, ?> r) {
            return r.getNumber() == this.upstreamBuild && pointsTo(r.getParent());
        }

        @Exported(visibility = 3)
        public String getUpstreamProject() {
            return this.upstreamProject;
        }

        @Exported(visibility = 3)
        public int getUpstreamBuild() {
            return this.upstreamBuild;
        }

        @CheckForNull
        public Run<?, ?> getUpstreamRun() {
            Job<?, ?> job = (Job) Jenkins.get().getItemByFullName(this.upstreamProject, Job.class);
            if (job != null) {
                return job.mo10getBuildByNumber(this.upstreamBuild);
            }
            return null;
        }

        @Exported(visibility = 3)
        public String getUpstreamUrl() {
            return this.upstreamUrl;
        }

        public List<Cause> getUpstreamCauses() {
            return this.upstreamCauses;
        }

        @Override // hudson.model.Cause
        public String getShortDescription() {
            return Messages.Cause_UpstreamCause_ShortDescription(this.upstreamProject, Integer.valueOf(this.upstreamBuild));
        }

        @Override // hudson.model.Cause
        public void print(TaskListener listener) {
            print(listener, 0);
        }

        private void indent(TaskListener listener, int depth) {
            for (int i = 0; i < depth; i++) {
                listener.getLogger().print(' ');
            }
        }

        private void print(TaskListener listener, int depth) {
            indent(listener, depth);
            listener.getLogger().println(Messages.Cause_UpstreamCause_ShortDescription(ModelHyperlinkNote.encodeTo("/" + this.upstreamUrl, this.upstreamProject), ModelHyperlinkNote.encodeTo("/" + this.upstreamUrl + this.upstreamBuild, Integer.toString(this.upstreamBuild))));
            if (this.upstreamCauses != null && !this.upstreamCauses.isEmpty()) {
                indent(listener, depth);
                listener.getLogger().println(Messages.Cause_UpstreamCause_CausedBy());
                for (Cause cause : this.upstreamCauses) {
                    if (cause instanceof UpstreamCause) {
                        ((UpstreamCause) cause).print(listener, depth + 1);
                    } else {
                        indent(listener, depth + 1);
                        cause.print(listener);
                    }
                }
            }
        }

        public String toString() {
            return this.upstreamUrl + this.upstreamBuild + String.valueOf(this.upstreamCauses);
        }

        /* loaded from: Cause$UpstreamCause$ConverterImpl.class */
        public static class ConverterImpl extends XStream2.PassthruConverter<UpstreamCause> {
            public ConverterImpl(XStream2 xstream) {
                super(xstream);
            }

            /* JADX INFO: Access modifiers changed from: protected */
            @Override // hudson.util.XStream2.PassthruConverter
            public void callback(UpstreamCause uc, UnmarshallingContext context) {
                if (uc.upstreamCause != null) {
                    uc.upstreamCauses.add(uc.upstreamCause);
                    uc.upstreamCause = null;
                    OldDataMonitor.report(context, "1.288");
                }
            }
        }

        /* loaded from: Cause$UpstreamCause$DeeplyNestedUpstreamCause.class */
        public static class DeeplyNestedUpstreamCause extends Cause {
            @Override // hudson.model.Cause
            public String getShortDescription() {
                return "(deeply nested causes)";
            }

            public int hashCode() {
                return 11;
            }

            public boolean equals(Object obj) {
                return obj instanceof DeeplyNestedUpstreamCause;
            }

            public String toString() {
                return "JENKINS-14814";
            }

            @Override // hudson.model.Cause
            public void onLoad(@NonNull Job<?, ?> _job, int _buildNumber) {
            }
        }
    }

    @Deprecated
    /* loaded from: Cause$UserCause.class */
    public static class UserCause extends Cause {
        private String authenticationName = Jenkins.getAuthentication2().getName();

        @Exported(visibility = 3)
        public String getUserName() {
            User user = User.getById(this.authenticationName, false);
            return user != null ? user.getDisplayName() : this.authenticationName;
        }

        @Override // hudson.model.Cause
        public String getShortDescription() {
            return Messages.Cause_UserCause_ShortDescription(this.authenticationName);
        }

        public boolean equals(Object o) {
            return (o instanceof UserCause) && Arrays.equals(new Object[]{this.authenticationName}, new Object[]{((UserCause) o).authenticationName});
        }

        public int hashCode() {
            return 295 + (this.authenticationName != null ? this.authenticationName.hashCode() : 0);
        }
    }

    /* loaded from: Cause$UserIdCause.class */
    public static class UserIdCause extends Cause {

        @CheckForNull
        private String userId;

        public UserIdCause() {
            User user = User.current();
            this.userId = user == null ? null : user.getId();
        }

        public UserIdCause(@CheckForNull String userId) {
            this.userId = userId;
        }

        @Exported(visibility = 3)
        @CheckForNull
        public String getUserId() {
            return this.userId;
        }

        @NonNull
        private String getUserIdOrUnknown() {
            return this.userId != null ? this.userId : User.getUnknown().getId();
        }

        private User getUser() {
            if (this.userId == null) {
                return null;
            }
            return User.getById(this.userId, false);
        }

        @Exported(visibility = 3)
        public String getUserName() {
            User user = getUser();
            return user == null ? "anonymous" : user.getDisplayName();
        }

        @CheckForNull
        @Restricted({DoNotUse.class})
        public String getUserUrl() {
            User user = getUser();
            if (user != null) {
                return user.getUrl();
            }
            return null;
        }

        @CheckForNull
        @Restricted({DoNotUse.class})
        public String getUserAvatar() {
            User user = getUser();
            if (user != null) {
                return Functions.getAvatar(user, "48x48");
            }
            return null;
        }

        @Override // hudson.model.Cause
        public String getShortDescription() {
            return Messages.Cause_UserIdCause_ShortDescription(getUserName());
        }

        @Override // hudson.model.Cause
        public void print(TaskListener listener) {
            User user = getUserId() == null ? null : User.getById(getUserId(), false);
            if (user != null) {
                listener.getLogger().println(Messages.Cause_UserIdCause_ShortDescription(ModelHyperlinkNote.encodeTo(user)));
            } else {
                listener.getLogger().println(Messages.Cause_UserIdCause_ShortDescription("unknown or anonymous"));
            }
        }

        public boolean equals(Object o) {
            return (o instanceof UserIdCause) && Objects.equals(this.userId, ((UserIdCause) o).userId);
        }

        public int hashCode() {
            return Objects.hash(this.userId);
        }
    }

    /* loaded from: Cause$RemoteCause.class */
    public static class RemoteCause extends Cause {
        private String addr;
        private String note;

        public RemoteCause(String host, String note) {
            this.addr = host;
            this.note = note;
        }

        @Override // hudson.model.Cause
        public String getShortDescription() {
            if (this.note != null) {
                return Messages.Cause_RemoteCause_ShortDescriptionWithNote(this.addr, this.note);
            }
            return Messages.Cause_RemoteCause_ShortDescription(this.addr);
        }

        @Exported(visibility = 3)
        public String getAddr() {
            return this.addr;
        }

        @Exported(visibility = 3)
        public String getNote() {
            return this.note;
        }

        public boolean equals(Object o) {
            return (o instanceof RemoteCause) && Objects.equals(this.addr, ((RemoteCause) o).addr) && Objects.equals(this.note, ((RemoteCause) o).note);
        }

        public int hashCode() {
            return Objects.hash(this.addr, this.note);
        }
    }
}
