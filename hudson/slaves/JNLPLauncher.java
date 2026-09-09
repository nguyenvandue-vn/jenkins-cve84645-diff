package hudson.slaves;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.model.identity.InstanceIdentityProvider;
import jenkins.security.XStreamDeserializable;
import jenkins.slaves.RemotingWorkDirSettings;
import jenkins.util.SystemProperties;
import jenkins.websocket.WebSockets;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/* loaded from: JNLPLauncher.class */
public class JNLPLauncher extends ComputerLauncher {

    @CheckForNull
    @SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public String tunnel;

    @XStreamDeserializable
    @Deprecated
    public final transient String vmargs;

    @NonNull
    private RemotingWorkDirSettings workDirSettings;
    private boolean webSocket;

    @NonNull
    @Restricted({NoExternalUse.class})
    public static final String CUSTOM_INBOUND_URL_PROPERTY = "jenkins.agent.inboundUrl";

    @Restricted({NoExternalUse.class})
    @Deprecated
    public static Descriptor<ComputerLauncher> DESCRIPTOR;

    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel, @CheckForNull String vmargs, @CheckForNull RemotingWorkDirSettings workDirSettings) {
        this(tunnel, vmargs);
        if (workDirSettings != null) {
            setWorkDirSettings(workDirSettings);
        }
    }

    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel) {
        this.vmargs = null;
        this.workDirSettings = RemotingWorkDirSettings.getEnabledDefaults();
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
    }

    @Deprecated
    public JNLPLauncher(@CheckForNull String tunnel, @CheckForNull String vmargs) {
        this.vmargs = null;
        this.workDirSettings = RemotingWorkDirSettings.getEnabledDefaults();
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
    }

    @DataBoundConstructor
    public JNLPLauncher() {
        this.vmargs = null;
        this.workDirSettings = RemotingWorkDirSettings.getEnabledDefaults();
    }

    /* JADX WARN: Illegal instructions before constructor call */
    @java.lang.Deprecated
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public JNLPLauncher(boolean r6) {
        /*
            r5 = this;
            r0 = r5
            r1 = 0
            r2 = 0
            r3 = r6
            if (r3 == 0) goto Ld
            jenkins.slaves.RemotingWorkDirSettings r3 = jenkins.slaves.RemotingWorkDirSettings.getEnabledDefaults()
            goto L10
        Ld:
            jenkins.slaves.RemotingWorkDirSettings r3 = jenkins.slaves.RemotingWorkDirSettings.getDisabledDefaults()
        L10:
            r0.<init>(r1, r2, r3)
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: hudson.slaves.JNLPLauncher.<init>(boolean):void");
    }

    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"}, justification = "workDirSettings in readResolve is needed for data migration.")
    protected Object readResolve() {
        if (this.workDirSettings == null) {
            this.workDirSettings = RemotingWorkDirSettings.getDisabledDefaults();
        }
        return this;
    }

    public RemotingWorkDirSettings getWorkDirSettings() {
        return this.workDirSettings;
    }

    @DataBoundSetter
    public final void setWorkDirSettings(@NonNull RemotingWorkDirSettings workDirSettings) {
        this.workDirSettings = workDirSettings;
    }

    public boolean isLaunchSupported() {
        return false;
    }

    public boolean isWebSocket() {
        return this.webSocket;
    }

    @DataBoundSetter
    public void setWebSocket(boolean webSocket) {
        this.webSocket = webSocket;
    }

    public String getTunnel() {
        return this.tunnel;
    }

    @DataBoundSetter
    public void setTunnel(String tunnel) {
        this.tunnel = Util.fixEmptyAndTrim(tunnel);
    }

    public void launch(SlaveComputer computer, TaskListener listener) {
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public String getRemotingOptionsUnix(@NonNull Computer computer) {
        return getRemotingOptions(escapeUnix(computer.getName()));
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public String getRemotingOptionsWindows(@NonNull Computer computer) {
        return getRemotingOptions(escapeWindows(computer.getName()));
    }

    @Restricted({DoNotUse.class})
    public boolean isConfigured() {
        return this.webSocket || this.tunnel != null || this.workDirSettings.isConfigured();
    }

    private String getRemotingOptions(String computerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("-name ");
        sb.append(computerName);
        sb.append(' ');
        sb.append("-webSocket ");
        if (this.tunnel != null) {
            sb.append(" -tunnel ");
            sb.append(this.tunnel);
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String escapeUnix(@NonNull String input) {
        if (!input.isEmpty() && input.chars().allMatch(Character::isLetterOrDigit)) {
            return input;
        }
        Escaper escaper = Escapers.builder().addEscape('\"', "\\\"").addEscape('`', "\\`").build();
        return "\"" + escaper.escape(input) + "\"";
    }

    private static String escapeWindows(@NonNull String input) {
        if (!input.isEmpty() && input.chars().allMatch(Character::isLetterOrDigit)) {
            return input;
        }
        Escaper escaper = Escapers.builder().addEscape('\"', "\\\"").build();
        return "\"" + escaper.escape(input) + "\"";
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public String getWorkDirOptions(@NonNull Computer computer) {
        if (!(computer instanceof SlaveComputer)) {
            return "";
        }
        return this.workDirSettings.toCommandLineString((SlaveComputer) computer);
    }

    @Extension
    @Symbol({"inbound", "jnlp"})
    /* loaded from: JNLPLauncher$DescriptorImpl.class */
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        @SuppressFBWarnings(value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"}, justification = "for backward compatibility")
        public DescriptorImpl() {
            JNLPLauncher.DESCRIPTOR = this;
        }

        @NonNull
        public String getDisplayName() {
            return Messages.JNLPLauncher_displayName();
        }

        public boolean isWorkDirSupported() {
            return DescriptorImpl.class.equals(getClass());
        }

        @Restricted({DoNotUse.class})
        public boolean isTcpSupported() {
            return Jenkins.get().getTcpSlaveAgentListener() != null;
        }

        @Restricted({DoNotUse.class})
        public boolean isInstanceIdentityInstalled() {
            return (InstanceIdentityProvider.RSA.getCertificate() == null || InstanceIdentityProvider.RSA.getPrivateKey() == null) ? false : true;
        }

        @Restricted({DoNotUse.class})
        public boolean isWebSocketSupported() {
            return WebSockets.isSupported();
        }
    }

    @Restricted({NoExternalUse.class})
    public static String getInboundAgentUrl() {
        String url = SystemProperties.getString(CUSTOM_INBOUND_URL_PROPERTY);
        if (url == null || url.isEmpty()) {
            return Jenkins.get().getRootUrl();
        }
        return url;
    }
}
