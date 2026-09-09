package hudson.slaves;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.User;
import java.io.ObjectStreamException;
import java.util.Collections;
import jenkins.agents.IOfflineCause;
import jenkins.model.Jenkins;
import jenkins.security.XStreamDeserializable;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
/* loaded from: OfflineCause.class */
public abstract class OfflineCause implements IOfflineCause {
    protected final long timestamp = System.currentTimeMillis();

    @Exported
    public long getTimestamp() {
        return this.timestamp;
    }

    @Restricted({NoExternalUse.class})
    @Deprecated
    /* loaded from: OfflineCause$LegacyOfflineCause.class */
    public static class LegacyOfflineCause extends OfflineCause {
        @Exported(name = "description")
        public String toString() {
            return "";
        }
    }

    /* loaded from: OfflineCause$SimpleOfflineCause.class */
    public static class SimpleOfflineCause extends OfflineCause {
        public final Localizable description;

        protected SimpleOfflineCause(Localizable description) {
            this.description = description;
        }

        @Exported(name = "description")
        public String toString() {
            return this.description.toString();
        }
    }

    public static OfflineCause create(Localizable d) {
        if (d == null) {
            return null;
        }
        return new SimpleOfflineCause(d);
    }

    /* loaded from: OfflineCause$ChannelTermination.class */
    public static class ChannelTermination extends OfflineCause {
        public final Exception cause;

        public ChannelTermination(Exception cause) {
            this.cause = cause;
        }

        public String getShortDescription() {
            return this.cause.toString();
        }

        public String toString() {
            return Messages.OfflineCause_connection_was_broken_simple();
        }
    }

    /* loaded from: OfflineCause$LaunchFailed.class */
    public static class LaunchFailed extends OfflineCause {
        public String toString() {
            return Messages.OfflineCause_LaunchFailed();
        }
    }

    /* loaded from: OfflineCause$UserCause.class */
    public static class UserCause extends SimpleOfflineCause {

        @XStreamDeserializable
        @Deprecated
        private transient User user;

        @CheckForNull
        private String userId;
        private final String message;

        public UserCause(@CheckForNull User user, @CheckForNull String message) {
            this(user != null ? user.getId() : null, message);
        }

        private UserCause(String userId, String message) {
            super(Messages._SlaveComputer_DisconnectedBy(userId != null ? userId : Jenkins.ANONYMOUS2.getName(), message != null ? " : " + message : ""));
            this.message = message;
            this.userId = userId;
        }

        public User getUser() {
            if (this.userId == null) {
                return User.getUnknown();
            }
            return User.getById(this.userId, true);
        }

        public String getMessage() {
            return Util.escape(this.message);
        }

        private Object readResolve() throws ObjectStreamException {
            if (this.user != null) {
                String id = this.user.getId();
                if (id != null) {
                    this.userId = id;
                } else {
                    User user = User.get(this.user.getFullName(), true, Collections.emptyMap());
                    this.userId = user.getId();
                }
                this.user = null;
            }
            return this;
        }

        @NonNull
        public String getComputerIcon() {
            return "symbol-computer-disconnected";
        }

        @NonNull
        public String getComputerIconAltText() {
            return "[temporarily offline by user]";
        }

        @NonNull
        public String getIcon() {
            return "symbol-person";
        }
    }

    /* loaded from: OfflineCause$ByCLI.class */
    public static class ByCLI extends UserCause {

        @Exported
        public final String message;

        public ByCLI(String message) {
            super(User.current(), message);
            this.message = message;
        }
    }

    /* loaded from: OfflineCause$IdleOfflineCause.class */
    public static class IdleOfflineCause extends SimpleOfflineCause {
        public IdleOfflineCause() {
            super(Messages._RetentionStrategy_Demand_OfflineIdle());
        }

        @NonNull
        public String getComputerIcon() {
            return "symbol-computer-paused";
        }

        @NonNull
        public String getComputerIconAltText() {
            return "[will connect automatically whenever needed]";
        }

        @NonNull
        public String getIcon() {
            return "symbol-pause";
        }

        public String getStatusClass() {
            return "info";
        }
    }
}
