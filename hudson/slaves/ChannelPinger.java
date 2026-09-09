package hudson.slaves;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import hudson.slaves.OfflineCause;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.PingFailureAnalyzer;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
/* loaded from: ChannelPinger.class */
public class ChannelPinger extends ComputerListener {
    static final int PING_TIMEOUT_SECONDS_DEFAULT = 240;
    static final int PING_INTERVAL_SECONDS_DEFAULT = 300;
    private static final Logger LOGGER = Logger.getLogger(ChannelPinger.class.getName());
    private static final String TIMEOUT_SECONDS_PROPERTY = ChannelPinger.class.getName() + ".pingTimeoutSeconds";
    private static final String INTERVAL_MINUTES_PROPERTY_DEPRECATED = ChannelPinger.class.getName() + ".pingInterval";
    private static final String INTERVAL_SECONDS_PROPERTY = ChannelPinger.class.getName() + ".pingIntervalSeconds";
    private Duration pingTimeout = SystemProperties.getDuration(TIMEOUT_SECONDS_PROPERTY, ChronoUnit.SECONDS, Duration.ofSeconds(240));
    private Duration pingInterval;

    public ChannelPinger() {
        this.pingInterval = Duration.ofSeconds(300L);
        Duration interval = SystemProperties.getDuration(INTERVAL_SECONDS_PROPERTY, ChronoUnit.SECONDS, (Duration) null);
        if (interval == null) {
            interval = SystemProperties.getDuration(INTERVAL_MINUTES_PROPERTY_DEPRECATED, ChronoUnit.MINUTES, (Duration) null);
            if (interval != null) {
                LOGGER.warning(INTERVAL_MINUTES_PROPERTY_DEPRECATED + " property is deprecated, " + INTERVAL_SECONDS_PROPERTY + " should be used");
            }
        }
        if (interval != null) {
            this.pingInterval = interval;
        }
    }

    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) {
        SlaveComputer slaveComputer = null;
        if (c instanceof SlaveComputer) {
            slaveComputer = (SlaveComputer) c;
        }
        install(channel, slaveComputer);
    }

    public void install(Channel channel) {
        install(channel, null);
    }

    @VisibleForTesting
    void install(Channel channel, @CheckForNull SlaveComputer c) {
        int pingTimeoutSeconds = (int) this.pingTimeout.toSeconds();
        int pingIntervalSeconds = (int) this.pingInterval.toSeconds();
        if (pingTimeoutSeconds < 1 || pingIntervalSeconds < 1) {
            LOGGER.warning("Agent ping is disabled");
            return;
        }
        try {
            channel.call(new SetUpRemotePing(pingTimeoutSeconds, pingIntervalSeconds));
            LOGGER.fine("Set up a remote ping for " + channel.getName());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set up a ping for " + channel.getName(), (Throwable) e);
        }
        setUpPingForChannel(channel, c, pingTimeoutSeconds, pingIntervalSeconds, true);
    }

    @VisibleForTesting
    @Restricted({NoExternalUse.class})
    /* loaded from: ChannelPinger$SetUpRemotePing.class */
    public static class SetUpRemotePing extends MasterToSlaveCallable<Void, IOException> {
        private static final long serialVersionUID = -2702219700841759872L;

        @Deprecated
        private transient int pingInterval;
        private final int pingTimeoutSeconds;
        private final int pingIntervalSeconds;

        public SetUpRemotePing(int pingTimeoutSeconds, int pingIntervalSeconds) {
            this.pingTimeoutSeconds = pingTimeoutSeconds;
            this.pingIntervalSeconds = pingIntervalSeconds;
        }

        /* renamed from: call, reason: merged with bridge method [inline-methods] */
        public Void m79call() throws IOException {
            ChannelPinger.setUpPingForChannel(getOpenChannelOrFail(), null, this.pingTimeoutSeconds, this.pingIntervalSeconds, false);
            return null;
        }

        public int hashCode() {
            return Objects.hash(Integer.valueOf(this.pingIntervalSeconds), Integer.valueOf(this.pingTimeoutSeconds));
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            SetUpRemotePing other = (SetUpRemotePing) obj;
            return this.pingIntervalSeconds == other.pingIntervalSeconds && this.pingTimeoutSeconds == other.pingTimeoutSeconds;
        }

        protected Object readResolve() {
            if (this.pingInterval != 0) {
                return new SetUpRemotePing(ChannelPinger.PING_TIMEOUT_SECONDS_DEFAULT, this.pingInterval * 60);
            }
            return this;
        }
    }

    @VisibleForTesting
    @Restricted({NoExternalUse.class})
    public static void setUpPingForChannel(final Channel channel, final SlaveComputer computer, int timeoutSeconds, int intervalSeconds, final boolean analysis) {
        LOGGER.log(Level.FINE, "setting up ping on {0} with a {1} seconds interval and {2} seconds timeout", new Object[]{channel.getName(), Integer.valueOf(intervalSeconds), Integer.valueOf(timeoutSeconds)});
        final AtomicBoolean isInClosed = new AtomicBoolean(false);
        final PingThread t = new PingThread(channel, TimeUnit.SECONDS.toMillis(timeoutSeconds), TimeUnit.SECONDS.toMillis(intervalSeconds)) { // from class: hudson.slaves.ChannelPinger.1
            protected void onDead(Throwable cause) {
                if (analysis) {
                    analyze(cause);
                }
                boolean inClosed = isInClosed.get();
                if (computer != null) {
                    Exception exception = cause instanceof Exception ? (Exception) cause : new IOException(cause);
                    computer.disconnect(new OfflineCause.ChannelTermination(exception));
                }
                if (inClosed) {
                    ChannelPinger.LOGGER.log(Level.FINE, "Ping failed after the channel " + channel.getName() + " is already partially closed.", cause);
                    return;
                }
                ChannelPinger.LOGGER.log(Level.INFO, "Ping failed. Terminating the channel " + channel.getName() + ".", cause);
                if (computer == null) {
                    try {
                        channel.close(cause);
                    } catch (IOException x) {
                        ChannelPinger.LOGGER.log(Level.WARNING, "could not disconnect " + channel.getName(), (Throwable) x);
                    }
                }
            }

            private void analyze(Throwable cause) {
                Iterator it = PingFailureAnalyzer.all().iterator();
                while (it.hasNext()) {
                    PingFailureAnalyzer pfa = (PingFailureAnalyzer) it.next();
                    try {
                        pfa.onPingFailure(channel, cause);
                    } catch (IOException ex) {
                        ChannelPinger.LOGGER.log(Level.WARNING, "Ping failure analyzer " + pfa.getClass().getName() + " failed for " + channel.getName(), (Throwable) ex);
                    }
                }
            }

            @Deprecated
            protected void onDead() {
                onDead(null);
            }
        };
        channel.addListener(new Channel.Listener() { // from class: hudson.slaves.ChannelPinger.2
            public void onClosed(Channel channel2, IOException cause) {
                ChannelPinger.LOGGER.fine("Terminating ping thread for " + channel2.getName());
                isInClosed.set(true);
                t.interrupt();
            }
        });
        t.start();
        LOGGER.log(Level.FINE, "Ping thread started for {0} with a {1} seconds interval and a {2} seconds timeout", new Object[]{channel, Integer.valueOf(intervalSeconds), Integer.valueOf(timeoutSeconds)});
    }
}
