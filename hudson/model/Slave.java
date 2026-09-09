package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.cli.CLI;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.WorkspaceLocator;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@SuppressFBWarnings(value = {"DESERIALIZATION_GADGET"}, justification = "unhappy about existence of readResolve?")
/* loaded from: Slave.class */
public abstract class Slave extends Node implements Serializable {
    private static final Logger LOGGER;
    protected String name;
    private String description;
    protected final String remoteFS;
    private int numExecutors;
    private Node.Mode mode;
    private RetentionStrategy retentionStrategy;
    private ComputerLauncher launcher;
    private String label;
    private DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties;

    @Deprecated
    private transient String userId;
    private transient Set<LabelAtom> previouslyAssignedLabels;

    @CheckForNull
    private transient Set<LabelAtom> labelAtomSet;
    private static final ThreadLocal<Boolean> insideReadResolve;

    @Deprecated
    private transient String agentCommand;
    private static final String WORKSPACE_ROOT;
    private static final Set<String> ALLOWED_JNLPJARS_FILES;
    static final /* synthetic */ boolean $assertionsDisabled;

    static {
        $assertionsDisabled = !Slave.class.desiredAssertionStatus();
        LOGGER = Logger.getLogger(Slave.class.getName());
        insideReadResolve = ThreadLocal.withInitial(() -> {
            return false;
        });
        WORKSPACE_ROOT = SystemProperties.getString(Slave.class.getName() + ".workspaceRoot", "workspace");
        ALLOWED_JNLPJARS_FILES = Set.of("agent.jar", "slave.jar", "remoting.jar", "jenkins-cli.jar", "hudson-cli.jar");
    }

    @Deprecated
    protected Slave(String name, String nodeDescription, String remoteFS, String numExecutors, Node.Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
        this(name, nodeDescription, remoteFS, Util.tryParseNumber(numExecutors, 1).intValue(), mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    @Deprecated
    protected Slave(String name, String nodeDescription, String remoteFS, int numExecutors, Node.Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws Descriptor.FormException, IOException {
        this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }

    protected Slave(@NonNull String name, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        this.numExecutors = 1;
        this.mode = Node.Mode.NORMAL;
        this.label = "";
        this.nodeProperties = new DescribableList<>(this);
        this.previouslyAssignedLabels = new HashSet();
        this.name = name;
        this.remoteFS = remoteFS;
        this.launcher = launcher;
        this.labelAtomSet = Collections.unmodifiableSet(Label.parse(this.label));
    }

    @Deprecated
    protected Slave(@NonNull String name, String nodeDescription, String remoteFS, int numExecutors, Node.Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
        this.numExecutors = 1;
        this.mode = Node.Mode.NORMAL;
        this.label = "";
        this.nodeProperties = new DescribableList<>(this);
        this.previouslyAssignedLabels = new HashSet();
        this.name = name;
        this.description = nodeDescription;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.remoteFS = Util.fixNull(remoteFS).trim();
        _setLabelString(labelString);
        this.launcher = launcher;
        this.retentionStrategy = retentionStrategy;
        getAssignedLabels();
        this.nodeProperties.replaceBy(nodeProperties);
        if (name.isEmpty()) {
            throw new Descriptor.FormException(Messages.Slave_InvalidConfig_NoName(), (String) null);
        }
        if (this.numExecutors <= 0) {
            throw new Descriptor.FormException(Messages.Slave_InvalidConfig_Executors(name), (String) null);
        }
    }

    @Restricted({DoNotUse.class})
    @RestrictedSince("2.220")
    @Deprecated
    public String getUserId() {
        return this.userId;
    }

    @Restricted({DoNotUse.class})
    @RestrictedSince("2.220")
    @Deprecated
    public void setUserId(String userId) {
    }

    public ComputerLauncher getLauncher() {
        if (this.launcher == null && this.agentCommand != null && !this.agentCommand.isEmpty()) {
            try {
                this.launcher = (ComputerLauncher) Jenkins.get().getPluginManager().uberClassLoader.loadClass("hudson.slaves.CommandLauncher").getConstructor(String.class, EnvVars.class).newInstance(this.agentCommand, null);
                this.agentCommand = null;
                save();
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "could not update historical agentCommand setting to CommandLauncher", (Throwable) x);
            }
        }
        return this.launcher == null ? new JNLPLauncher() : this.launcher;
    }

    @Override // hudson.model.Node
    @Deprecated
    public void save() throws IOException {
        super.save();
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public String getRemoteFS() {
        return this.remoteFS;
    }

    @Override // hudson.model.Node
    @NonNull
    public String getNodeName() {
        return this.name;
    }

    public String toString() {
        return getClass().getName() + "[" + this.name + "]";
    }

    @Override // hudson.model.Node
    public void setNodeName(String name) {
        this.name = name;
    }

    @DataBoundSetter
    public void setNodeDescription(String value) {
        this.description = value;
    }

    @Override // hudson.model.Node
    public String getNodeDescription() {
        return this.description;
    }

    @Override // hudson.model.Node
    public int getNumExecutors() {
        return this.numExecutors;
    }

    @DataBoundSetter
    public void setNumExecutors(int n) {
        this.numExecutors = n;
    }

    @Override // hudson.model.Node
    public Node.Mode getMode() {
        return this.mode;
    }

    @DataBoundSetter
    public void setMode(Node.Mode mode) {
        this.mode = mode;
    }

    @Override // hudson.model.Node
    @NonNull
    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        if ($assertionsDisabled || this.nodeProperties != null) {
            return this.nodeProperties;
        }
        throw new AssertionError();
    }

    @DataBoundSetter
    public void setNodeProperties(List<? extends NodeProperty<?>> properties) throws IOException {
        if (this.nodeProperties == null) {
            warnPlugin();
            this.nodeProperties = new DescribableList<>(this);
        }
        this.nodeProperties.replaceBy(properties);
    }

    public RetentionStrategy getRetentionStrategy() {
        return this.retentionStrategy == null ? RetentionStrategy.Always.INSTANCE : this.retentionStrategy;
    }

    @DataBoundSetter
    public void setRetentionStrategy(RetentionStrategy availabilityStrategy) {
        this.retentionStrategy = availabilityStrategy;
    }

    @Override // hudson.model.Node
    public String getLabelString() {
        return Util.fixNull(this.label).trim();
    }

    @Override // hudson.model.Node
    @NonNull
    @Restricted({NoExternalUse.class})
    public Set<LabelAtom> drainLabelsToTrim() {
        HashSet<LabelAtom> result = new HashSet<>(super.drainLabelsToTrim());
        synchronized (this.previouslyAssignedLabels) {
            result.addAll(this.previouslyAssignedLabels);
            this.previouslyAssignedLabels.clear();
        }
        return result;
    }

    @Override // hudson.model.Node
    @DataBoundSetter
    public void setLabelString(String labelString) throws IOException {
        _setLabelString(labelString);
        getAssignedLabels();
    }

    private void _setLabelString(String labelString) {
        synchronized (this.previouslyAssignedLabels) {
            this.previouslyAssignedLabels.addAll((Collection) getAssignedLabels().stream().filter((v0) -> {
                return Objects.nonNull(v0);
            }).collect(Collectors.toSet()));
        }
        this.label = Util.fixNull(labelString).trim();
        this.labelAtomSet = Collections.unmodifiableSet(Label.parse(this.label));
    }

    @Override // hudson.model.Node
    protected Set<LabelAtom> getLabelAtomSet() {
        if (this.labelAtomSet == null) {
            if (!insideReadResolve.get().booleanValue()) {
                warnPlugin();
            }
            this.labelAtomSet = Collections.unmodifiableSet(Label.parse(this.label));
        }
        return this.labelAtomSet;
    }

    private void warnPlugin() {
        LOGGER.log(Level.WARNING, () -> {
            return getClass().getName() + " or one of its superclass overrides readResolve() without calling super implementation.Please file an issue against the plugin implementing it: " + String.valueOf(Jenkins.get().getPluginManager().whichPlugin(getClass()));
        });
    }

    @Override // hudson.model.Node
    public Callable<ClockDifference, IOException> getClockDifferenceCallable() {
        return new GetClockDifference1();
    }

    @Override // hudson.model.Node
    public Computer createComputer() {
        return new SlaveComputer(this);
    }

    @Override // hudson.model.Node
    public FilePath getWorkspaceFor(TopLevelItem item) {
        Iterator it = WorkspaceLocator.all().iterator();
        while (it.hasNext()) {
            WorkspaceLocator l = (WorkspaceLocator) it.next();
            FilePath workspace = l.locate(item, this);
            if (workspace != null) {
                return workspace;
            }
        }
        FilePath r = getWorkspaceRoot();
        if (r == null) {
            return null;
        }
        return r.child(item.getFullName());
    }

    @Override // hudson.model.Node
    @CheckForNull
    public FilePath getRootPath() {
        SlaveComputer computer = getComputer();
        if (computer == null) {
            return null;
        }
        return createPath(Objects.toString(computer.getAbsoluteRemoteFs(), this.remoteFS));
    }

    @CheckForNull
    public FilePath getWorkspaceRoot() {
        FilePath r = getRootPath();
        if (r == null) {
            return null;
        }
        return r.child(WORKSPACE_ROOT);
    }

    /* loaded from: Slave$JnlpJar.class */
    public static final class JnlpJar implements HttpResponse {
        private final String fileName;

        public JnlpJar(String fileName) {
            this.fileName = fileName;
        }

        public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            URLConnection con = connect();
            rsp.setHeader("Content-Disposition", "attachment; filename=" + this.fileName);
            InputStream in = con.getInputStream();
            try {
                rsp.serveFile(req, in, con.getLastModified(), con.getContentLengthLong(), "*.jar");
                if (in != null) {
                    in.close();
                }
            } catch (Throwable th) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }

        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            doIndex(req, rsp);
        }

        private URLConnection connect() throws IOException {
            URL res = getURL();
            return res.openConnection();
        }

        public URL getURL() throws IOException {
            String name = this.fileName;
            if (!Slave.ALLOWED_JNLPJARS_FILES.contains(name)) {
                throw new MalformedURLException("The specified file path " + this.fileName + " is not allowed due to security reasons");
            }
            Class<?> owner = null;
            if (name.equals("hudson-cli.jar") || name.equals("jenkins-cli.jar")) {
                owner = CLI.class;
            } else if (name.equals("agent.jar") || name.equals("slave.jar") || name.equals("remoting.jar")) {
                owner = Launcher.class;
            }
            if (owner != null) {
                File jar = Which.jarFile(owner);
                if (jar.isFile()) {
                    name = "lib/" + jar.getName();
                } else {
                    URL res = findExecutableJar(jar, owner);
                    if (res != null) {
                        return res;
                    }
                }
            }
            URL res2 = Jenkins.get().getServletContext().getResource("/WEB-INF/" + name);
            if (res2 == null) {
                throw new FileNotFoundException(name);
            }
            Slave.LOGGER.log(Level.FINE, "found {0}", res2);
            return res2;
        }

        @CheckForNull
        private URL findExecutableJar(File notActuallyJAR, Class<?> mainClass) throws IOException {
            File[] siblings;
            if (notActuallyJAR.getName().equals("classes") && (siblings = notActuallyJAR.getParentFile().listFiles()) != null) {
                for (File actualJar : siblings) {
                    if (actualJar.getName().endsWith(".jar")) {
                        JarFile jf = new JarFile(actualJar, false);
                        try {
                            Manifest mf = jf.getManifest();
                            if (mf != null && mainClass.getName().equals(mf.getMainAttributes().getValue("Main-Class"))) {
                                Slave.LOGGER.log(Level.FINE, "found {0}", actualJar);
                                URL url = actualJar.toURI().toURL();
                                jf.close();
                                return url;
                            }
                            jf.close();
                        } catch (Throwable th) {
                            try {
                                jf.close();
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                            }
                            throw th;
                        }
                    }
                }
                return null;
            }
            return null;
        }

        public byte[] readFully() throws IOException {
            InputStream in = connect().getInputStream();
            try {
                byte[] readAllBytes = in.readAllBytes();
                if (in != null) {
                    in.close();
                }
                return readAllBytes;
            } catch (Throwable th) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
    }

    @Override // hudson.model.Node
    @NonNull
    public hudson.Launcher createLauncher(TaskListener listener) {
        SlaveComputer c = getComputer();
        if (c == null) {
            listener.error("Issue with creating launcher for agent " + this.name + ". Computer has been disconnected");
            return new Launcher.DummyLauncher(listener);
        }
        Slave node = c.getNode();
        if (node != this) {
            String message = "Issue with creating launcher for agent " + this.name + ". Computer has been reconnected";
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, message, (Throwable) new IllegalStateException("Computer has been reconnected, this Node instance cannot be used anymore"));
            }
            return new Launcher.DummyLauncher(listener);
        }
        Channel channel = c.getChannel();
        if (channel == null) {
            reportLauncherCreateError("The agent has not been fully initialized yet", "No remoting channel to the agent OR it has not been fully initialized yet", listener);
            return new Launcher.DummyLauncher(listener);
        }
        if (channel.isClosingOrClosed()) {
            reportLauncherCreateError("The agent is being disconnected", "Remoting channel is either in the process of closing down or has closed down", listener);
            return new Launcher.DummyLauncher(listener);
        }
        Boolean isUnix = c.isUnix();
        if (isUnix == null) {
            reportLauncherCreateError("The agent has not been fully initialized yet", "Cannot determine if the agent is a Unix one, the System status request has not completed yet. It is an invalid channel state, please report a bug to Jenkins if you see it.", listener);
            return new Launcher.DummyLauncher(listener);
        }
        return new Launcher.RemoteLauncher(listener, channel, isUnix.booleanValue()).decorateFor(this);
    }

    private void reportLauncherCreateError(@NonNull String humanReadableMsg, @CheckForNull String exceptionDetails, @NonNull TaskListener listener) {
        String message = "Issue with creating launcher for agent " + this.name + ". " + humanReadableMsg;
        listener.error(message);
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(Level.WARNING, message + "Probably there is a race condition with Agent reconnection or disconnection, check other log entries", (Throwable) new IllegalStateException(exceptionDetails != null ? exceptionDetails : humanReadableMsg));
        }
    }

    @CheckForNull
    public SlaveComputer getComputer() {
        return toComputer();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Slave that = (Slave) o;
        return this.name.equals(that.name);
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    protected Object readResolve() {
        if (this.nodeProperties == null) {
            this.nodeProperties = new DescribableList<>(this);
        }
        this.previouslyAssignedLabels = new HashSet();
        insideReadResolve.set(true);
        try {
            _setLabelString(this.label);
            insideReadResolve.set(false);
            return this;
        } catch (Throwable th) {
            insideReadResolve.set(false);
            throw th;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // hudson.model.Node
    /* renamed from: getDescriptor */
    public SlaveDescriptor mo39getDescriptor() {
        SlaveDescriptor descriptorOrDie = Jenkins.get().getDescriptorOrDie(getClass());
        if (descriptorOrDie instanceof SlaveDescriptor) {
            return descriptorOrDie;
        }
        throw new IllegalStateException(String.valueOf(descriptorOrDie.getClass()) + " needs to extend from SlaveDescriptor");
    }

    /* loaded from: Slave$SlaveDescriptor.class */
    public static abstract class SlaveDescriptor extends NodeDescriptor {
        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckRemoteFS(@QueryParameter String value) throws IOException {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error(Messages.Slave_Remote_Director_Mandatory());
            }
            if (value.startsWith("\\\\") || value.startsWith("/net/")) {
                return FormValidation.warning(Messages.Slave_Network_Mounted_File_System_Warning());
            }
            if (Util.isRelativePath(value)) {
                return FormValidation.warning(Messages.Slave_Remote_Relative_Path_Warning());
            }
            return FormValidation.ok();
        }

        @NonNull
        @Restricted({NoExternalUse.class})
        public final List<Descriptor<ComputerLauncher>> computerLauncherDescriptors(@CheckForNull Slave it) {
            DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> all = Jenkins.get().getDescriptorList(ComputerLauncher.class);
            return it == null ? DescriptorVisibilityFilter.applyType(this.clazz, all) : DescriptorVisibilityFilter.apply(it, all);
        }

        @NonNull
        @Restricted({NoExternalUse.class})
        public final List<Descriptor<RetentionStrategy<?>>> retentionStrategyDescriptors(@CheckForNull Slave it) {
            return it == null ? DescriptorVisibilityFilter.applyType(this.clazz, RetentionStrategy.all()) : DescriptorVisibilityFilter.apply(it, RetentionStrategy.all());
        }

        @NonNull
        @Restricted({NoExternalUse.class})
        public final List<NodePropertyDescriptor> nodePropertyDescriptors(@CheckForNull Slave it) {
            List<NodePropertyDescriptor> apply;
            List<NodePropertyDescriptor> result = new ArrayList<>();
            DescriptorExtensionList descriptorList = Jenkins.get().getDescriptorList(NodeProperty.class);
            if (it == null) {
                apply = DescriptorVisibilityFilter.applyType(this.clazz, descriptorList);
            } else {
                apply = DescriptorVisibilityFilter.apply(it, descriptorList);
            }
            for (NodePropertyDescriptor npd : apply) {
                if (npd.isApplicable(this.clazz)) {
                    result.add(npd);
                }
            }
            return result;
        }
    }

    /* loaded from: Slave$GetClockDifference1.class */
    private static final class GetClockDifference1 extends MasterToSlaveCallable<ClockDifference, IOException> {
        private static final long serialVersionUID = 1;

        private GetClockDifference1() {
        }

        /* renamed from: call, reason: merged with bridge method [inline-methods] */
        public ClockDifference m53call() {
            return new ClockDifference(0L);
        }

        private Object writeReplace() {
            return new GetClockDifference2();
        }
    }

    /* loaded from: Slave$GetClockDifference2.class */
    private static final class GetClockDifference2 extends MasterToSlaveCallable<GetClockDifference3, IOException> {
        private final long startTime = System.currentTimeMillis();
        private static final long serialVersionUID = 1;

        private GetClockDifference2() {
        }

        /* renamed from: call, reason: merged with bridge method [inline-methods] */
        public GetClockDifference3 m54call() {
            return new GetClockDifference3(this.startTime);
        }
    }

    /* loaded from: Slave$GetClockDifference3.class */
    private static final class GetClockDifference3 implements Serializable {
        private final long remoteTime = System.currentTimeMillis();
        private final long startTime;

        GetClockDifference3(long startTime) {
            this.startTime = startTime;
        }

        private Object readResolve() {
            long endTime = System.currentTimeMillis();
            return new ClockDifference(((this.startTime + endTime) / 2) - this.remoteTime);
        }
    }
}
