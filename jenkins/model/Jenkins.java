package jenkins.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Lookup;
import hudson.Main;
import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.RestrictedSince;
import hudson.TcpSlaveAgentListener;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.init.TermMilestone;
import hudson.init.TerminatorFinder;
import hudson.lifecycle.Lifecycle;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.logging.LogRecorderManager;
import hudson.markup.EscapedMarkupFormatter;
import hudson.markup.MarkupFormatter;
import hudson.model.AbstractCIBase;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AdministrativeMonitor;
import hudson.model.AllView;
import hudson.model.Api;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Failure;
import hudson.model.Fingerprint;
import hudson.model.FingerprintCleanupThread;
import hudson.model.FingerprintMap;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Label;
import hudson.model.ListView;
import hudson.model.LoadBalancer;
import hudson.model.LoadStatistics;
import hudson.model.ManageJenkinsAction;
import hudson.model.ManagementLink;
import hudson.model.Messages;
import hudson.model.ModifiableViewGroup;
import hudson.model.NoFingerprintMatch;
import hudson.model.Node;
import hudson.model.OverallLoadStatistics;
import hudson.model.PaneStatusProperties;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.RestartListener;
import hudson.model.RootAction;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.UnprotectedRootAction;
import hudson.model.UpdateCenter;
import hudson.model.User;
import hudson.model.View;
import hudson.model.ViewGroupMixIn;
import hudson.model.WorkspaceCleanupThread;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SCMListener;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.Callable;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.FederatedLoginService;
import hudson.security.HudsonFilter;
import hudson.security.LegacyAuthorizationStrategy;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.security.SecurityMode;
import hudson.security.SecurityRealm;
import hudson.security.csrf.CrumbIssuer;
import hudson.security.csrf.GlobalCrumbIssuerConfiguration;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeList;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.SafeTimerTask;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AdministrativeError;
import hudson.util.ClockDifference;
import hudson.util.ComboBoxModel;
import hudson.util.CopyOnWriteList;
import hudson.util.CopyOnWriteMap;
import hudson.util.DaemonThreadFactory;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.Futures;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import hudson.util.JenkinsReloadFailed;
import hudson.util.LogTaskListener;
import hudson.util.MultipartFormDataParser;
import hudson.util.NamingThreadFactory;
import hudson.util.PluginServletFilter;
import hudson.util.QuotedStringTokenizer;
import hudson.util.RemotingDiagnostics;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import hudson.views.DefaultMyViewsTabBar;
import hudson.views.DefaultViewsTabBar;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import hudson.widgets.Widget;
import io.jenkins.servlet.RequestDispatcherWrapper;
import io.jenkins.servlet.ServletContextWrapper;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.Cookie;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import javax.servlet.ServletContext;
import jenkins.AgentProtocol;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionRefreshException;
import jenkins.InitReactorRunner;
import jenkins.agents.CloudSet;
import jenkins.diagnostics.URICheckEncodingMonitor;
import jenkins.install.InstallState;
import jenkins.install.SetupWizard;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.ProjectNamingStrategy;
import jenkins.security.ClassFilterImpl;
import jenkins.security.ConfidentialStore;
import jenkins.security.MasterToSlaveCallable;
import jenkins.security.RedactSecretJsonInErrorMessageSanitizer;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.security.SecurityListener;
import jenkins.security.stapler.DoActionFilter;
import jenkins.security.stapler.StaplerDispatchValidator;
import jenkins.security.stapler.StaplerDispatchable;
import jenkins.security.stapler.StaplerFilteredActionListener;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.security.stapler.TypedFilter;
import jenkins.slaves.WorkspaceLocator;
import jenkins.util.JenkinsJVM;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.io.FileBoolean;
import jenkins.util.io.OnMaster;
import jenkins.util.xml.XMLUtils;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.apache.commons.logging.LogFactory;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.ReactorListener;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.framework.adjunct.AdjunctManager;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;
import org.kohsuke.stapler.jelly.JellyRequestDispatcher;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.xml.sax.InputSource;

@ExportedBean
/* loaded from: Jenkins.class */
public class Jenkins extends AbstractCIBase implements DirectlyModifiableTopLevelItemGroup, StaplerProxy, StaplerFallback, ModifiableViewGroup, AccessControlled, DescriptorByNameOwner, ModelObjectWithContextMenu, ModelObjectWithChildren, OnMaster, Loadable {
    private final transient Queue queue;
    private volatile transient boolean configLoaded;
    public final transient Lookup lookup;
    private String version;
    private transient String installStateName;

    @Deprecated
    private InstallState installState;
    private transient SetupWizard setupWizard;
    private int numExecutors;
    private Node.Mode mode;
    private Boolean useSecurity;
    private volatile AuthorizationStrategy authorizationStrategy;
    private volatile SecurityRealm securityRealm;
    private volatile boolean disableRememberMe;
    private ProjectNamingStrategy projectNamingStrategy;
    private String workspaceDir;
    private String buildsDir;
    private String systemMessage;
    private MarkupFormatter markupFormatter;
    public final transient File root;
    private volatile transient InitMilestone initLevel;
    final transient Map<String, TopLevelItem> items;
    private static Jenkins theInstance;

    @CheckForNull
    private volatile transient QuietDownInfo quietDownInfo;
    private volatile transient boolean terminating;

    @GuardedBy("Jenkins.class")
    private transient boolean cleanUpStarted;
    private static FileBoolean STARTUP_MARKER_FILE;
    private volatile List<JDK> jdks;
    private volatile transient DependencyGraph dependencyGraph;
    private transient Future<DependencyGraph> scheduledFutureDependencyGraph;
    private transient Future<DependencyGraph> calculatingFutureDependencyGraph;
    private transient Object dependencyGraphLock;
    private volatile ViewsTabBar viewsTabBar;
    private volatile MyViewsTabBar myViewsTabBar;
    private final transient Map<Class, ExtensionList> extensionLists;
    private final transient Map<Class, DescriptorExtensionList> descriptorLists;
    protected final transient ConcurrentMap<Node, Computer> computers;
    public final Hudson.CloudList clouds;

    @Deprecated
    protected volatile transient NodeList slaves;
    private final transient Nodes nodes;
    Integer quietPeriod;
    int scmCheckoutRetryCount;
    private final CopyOnWriteArrayList<View> views;
    private volatile String primaryView;
    private final transient ViewGroupMixIn viewGroupMixIn;
    private final transient FingerprintMap fingerprintMap;
    public final transient PluginManager pluginManager;

    @SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public volatile transient TcpSlaveAgentListener tcpSlaveAgentListener;
    private final transient Object tcpSlaveAgentListenerLock;
    private final transient CopyOnWriteList<SCMListener> scmListeners;
    private int slaveAgentPort;
    private static final boolean SLAVE_AGENT_PORT_ENFORCE;
    private String label;
    private static String nodeNameAndSelfLabelOverride;
    private volatile CrumbIssuer crumbIssuer;
    private final transient ConcurrentHashMap<String, Label> labels;

    @Exported
    public final transient OverallLoadStatistics overallLoad;

    @Exported
    public final transient LoadStatistics unlabeledLoad;
    public final transient NodeProvisioner unlabeledNodeProvisioner;

    @Restricted({NoExternalUse.class})
    @Deprecated
    public final transient NodeProvisioner overallNodeProvisioner;

    @Deprecated
    public final transient ServletContext servletContext;
    private final transient jakarta.servlet.ServletContext jakartaServletContext;
    private final transient List<Action> actions;
    private DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties;
    private DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties;
    public final transient List<AdministrativeMonitor> administrativeMonitors;
    private final transient List<Widget> widgets;
    private final transient AdjunctManager adjuncts;
    private final transient ItemGroupMixIn itemGroupMixIn;
    static JenkinsHolder HOLDER;
    private final transient String secretKey;
    private final transient UpdateCenter updateCenter;
    private Boolean noUsageStatistics;

    @Restricted({NoExternalUse.class})
    Boolean nodeRenameMigrationNeeded;

    @SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public volatile transient ProxyConfiguration proxy;
    private transient LogRecorderManager log;
    private final transient boolean oldJenkinsJVM;

    @NonNull
    private transient Set<LabelAtom> labelAtomSet;

    @SuppressFBWarnings(value = {"MS_MUTABLE_COLLECTION_PKGPROTECT"}, justification = "mutable to allow plugins to add additional extensions")
    public static final Set<String> ALLOWED_RESOURCE_EXTENSIONS;

    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL"}, justification = "cannot be made immutable without breaking compatibility")
    public static List<LogRecord> logRecords;
    public static final XStream XSTREAM;
    public static final XStream2 XSTREAM2;
    private static final int TWICE_CPU_NUM;
    final transient ExecutorService threadPoolForLoad;

    @Restricted({NoExternalUse.class})
    public static final String UNCOMPUTED_VERSION = "?";

    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String VERSION;

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String CHANGELOG_URL;

    @SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String VERSION_HASH;

    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String SESSION_HASH;

    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String RESOURCE_PATH;

    @SuppressFBWarnings(value = {"MS_CANNOT_BE_FINAL", "PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public static String VIEW_RESOURCE_PATH;

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean PARALLEL_LOAD;

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean KILL_AFTER_LOAD;

    @Deprecated
    public static boolean FLYWEIGHT_SUPPORT;

    @Restricted({NoExternalUse.class})
    @Deprecated
    public static boolean CONCURRENT_BUILD;
    private static final String WORKSPACE_DIRNAME;

    @Restricted({NoExternalUse.class})
    public static final String NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
    private static final String DEFAULT_BUILDS_DIR = "${ITEM_ROOTDIR}/builds";
    private static final String OLD_DEFAULT_WORKSPACES_DIR;
    private static final String DEFAULT_WORKSPACES_DIR = "${JENKINS_HOME}/workspace/${ITEM_FULL_NAME}";
    static final String BUILDS_DIR_PROP;
    static final String WORKSPACES_DIR_PROP;

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean AUTOMATIC_AGENT_LAUNCH;

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static int EXTEND_TIMEOUT_SECONDS;
    private static final Logger LOGGER;
    private static final SecureRandom RANDOM;
    public static final PermissionGroup PERMISSIONS;
    public static final Permission ADMINISTER;
    public static final Permission MANAGE;
    public static final Permission SYSTEM_READ;

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_MUTABLE_ARRAY"}, justification = "Not for external use")
    public static final Permission[] MANAGE_AND_SYSTEM_READ;
    public static final Permission READ;

    @Deprecated
    public static final Permission RUN_SCRIPTS;
    private static final Set<String> ALWAYS_READABLE_PATHS;
    public static final Authentication ANONYMOUS2;

    @Deprecated
    public static final org.acegisecurity.Authentication ANONYMOUS;
    static final /* synthetic */ boolean $assertionsDisabled;

    /* renamed from: getItems, reason: collision with other method in class */
    public /* bridge */ /* synthetic */ Collection m104getItems(Predicate pred) {
        return getItems((Predicate<TopLevelItem>) pred);
    }

    static {
        $assertionsDisabled = !Jenkins.class.desiredAssertionStatus();
        SLAVE_AGENT_PORT_ENFORCE = SystemProperties.getBoolean(Jenkins.class.getName() + ".slaveAgentPortEnforce", false);
        nodeNameAndSelfLabelOverride = SystemProperties.getString(Jenkins.class.getName() + ".nodeNameAndSelfLabelOverride");
        HOLDER = new JenkinsHolder() { // from class: jenkins.model.Jenkins.3
            @CheckForNull
            public Jenkins getInstance() {
                return Jenkins.theInstance;
            }
        };
        ALLOWED_RESOURCE_EXTENSIONS = new HashSet(Arrays.asList("js|css|jpeg|jpg|png|gif|html|htm".split("\\|")));
        logRecords = Collections.emptyList();
        TWICE_CPU_NUM = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        VERSION = UNCOMPUTED_VERSION;
        RESOURCE_PATH = "";
        VIEW_RESOURCE_PATH = "/resources/TBD";
        PARALLEL_LOAD = SystemProperties.getBoolean(Jenkins.class.getName() + ".parallelLoad", true);
        KILL_AFTER_LOAD = SystemProperties.getBoolean(Jenkins.class.getName() + ".killAfterLoad", false);
        FLYWEIGHT_SUPPORT = true;
        CONCURRENT_BUILD = true;
        WORKSPACE_DIRNAME = SystemProperties.getString(Jenkins.class.getName() + ".workspaceDirName", "workspace");
        NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP = Jenkins.class.getName() + ".nameValidationRejectsTrailingDot";
        OLD_DEFAULT_WORKSPACES_DIR = "${ITEM_ROOTDIR}/" + WORKSPACE_DIRNAME;
        BUILDS_DIR_PROP = Jenkins.class.getName() + ".buildsDir";
        WORKSPACES_DIR_PROP = Jenkins.class.getName() + ".workspacesDir";
        AUTOMATIC_AGENT_LAUNCH = SystemProperties.getBoolean(Jenkins.class.getName() + ".automaticAgentLaunch", true);
        EXTEND_TIMEOUT_SECONDS = (int) SystemProperties.getDuration(Jenkins.class.getName() + ".extendTimeoutSeconds", ChronoUnit.SECONDS, Duration.ofSeconds(15L)).toSeconds();
        LOGGER = Logger.getLogger(Jenkins.class.getName());
        RANDOM = new SecureRandom();
        PERMISSIONS = Permission.HUDSON_PERMISSIONS;
        ADMINISTER = Permission.HUDSON_ADMINISTER;
        MANAGE = new Permission(PERMISSIONS, "Manage", Messages._Jenkins_Manage_Description(), ADMINISTER, true, new PermissionScope[]{PermissionScope.JENKINS});
        SYSTEM_READ = new Permission(PERMISSIONS, "SystemRead", Messages._Jenkins_SystemRead_Description(), ADMINISTER, SystemProperties.getBoolean("jenkins.security.SystemReadPermission"), new PermissionScope[]{PermissionScope.JENKINS});
        MANAGE_AND_SYSTEM_READ = new Permission[]{MANAGE, SYSTEM_READ};
        READ = new Permission(PERMISSIONS, "Read", Messages._Hudson_ReadPermission_Description(), Permission.READ, PermissionScope.JENKINS);
        RUN_SCRIPTS = new Permission(PERMISSIONS, "RunScripts", Messages._Hudson_RunScriptsPermission_Description(), ADMINISTER, PermissionScope.JENKINS);
        ALWAYS_READABLE_PATHS = new HashSet(Arrays.asList("404", "_404", "_404_simple", "login", "loginError", "logout", "accessDenied", "adjuncts", "error", "oops", "signup", "tcpSlaveAgentListener", "federatedLoginService", "securityRealm"));
        String paths = SystemProperties.getString(Jenkins.class.getName() + ".additionalReadablePaths");
        if (paths != null) {
            LOGGER.info(() -> {
                return "SECURITY-2047 override: Adding the following paths to ALWAYS_READABLE_PATHS: " + paths;
            });
            ALWAYS_READABLE_PATHS.addAll((Collection) Arrays.stream(paths.split(",")).map((v0) -> {
                return v0.trim();
            }).collect(Collectors.toSet()));
        }
        ANONYMOUS2 = new AnonymousAuthenticationToken("anonymous", "anonymous", Set.of(new SimpleGrantedAuthority("anonymous")));
        ANONYMOUS = new org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken("anonymous", "anonymous", new GrantedAuthority[]{new GrantedAuthorityImpl("anonymous")});
        try {
            XStream2 xStream2 = new XStream2();
            XSTREAM2 = xStream2;
            XSTREAM = xStream2;
            XSTREAM.alias("jenkins", Jenkins.class);
            XSTREAM.alias("slave", DumbSlave.class);
            XSTREAM.alias("jdk", JDK.class);
            XSTREAM.alias("view", ListView.class);
            XSTREAM.alias("listView", ListView.class);
            XSTREAM2.addCriticalField(Jenkins.class, "securityRealm");
            XSTREAM2.addCriticalField(Jenkins.class, "authorizationStrategy");
            Node.Mode.class.getEnumConstants();
            if (!$assertionsDisabled && PERMISSIONS == null) {
                throw new AssertionError();
            }
            if (!$assertionsDisabled && ADMINISTER == null) {
                throw new AssertionError();
            }
        } catch (Error | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to load Jenkins.class", e);
            throw e;
        }
    }

    @Restricted({Beta.class})
    public void loadNode(File dir) throws IOException {
        getNodesObject().load(dir);
    }

    /* loaded from: Jenkins$CloudList.class */
    public static class CloudList extends DescribableList<Cloud, Descriptor<Cloud>> {
        public CloudList(Jenkins h) {
            super(h);
        }

        public CloudList() {
        }

        public Cloud getByName(String name) {
            Iterator it = iterator();
            while (it.hasNext()) {
                Cloud c = (Cloud) it.next();
                if (c.name.equals(name)) {
                    return c;
                }
            }
            return null;
        }

        protected void onModified() throws IOException {
            super.onModified();
            Jenkins.get().trimLabels();
        }
    }

    private static int getSlaveAgentPortInitialValue(int def) {
        return SystemProperties.getInteger(Jenkins.class.getName() + ".slaveAgentPort", Integer.valueOf(def)).intValue();
    }

    public jakarta.servlet.ServletContext getServletContext() {
        return this.jakartaServletContext;
    }

    @NonNull
    public static Jenkins get() throws IllegalStateException {
        Jenkins instance = getInstanceOrNull();
        if (instance == null) {
            throw new IllegalStateException("Jenkins.instance is missing. Read the documentation of Jenkins.getInstanceOrNull to see what you are doing wrong.");
        }
        return instance;
    }

    @NonNull
    @Deprecated
    public static Jenkins getActiveInstance() throws IllegalStateException {
        return get();
    }

    @CheckForNull
    @CLIResolver
    public static Jenkins getInstanceOrNull() {
        return HOLDER.getInstance();
    }

    @Nullable
    @Deprecated
    public static Jenkins getInstance() {
        return getInstanceOrNull();
    }

    protected Jenkins(File root, jakarta.servlet.ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root, context, null);
    }

    @SuppressFBWarnings({"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", "DM_EXIT"})
    protected Jenkins(File root, jakarta.servlet.ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
        this.configLoaded = false;
        this.lookup = new Lookup();
        this.version = "1.0";
        this.numExecutors = 2;
        this.mode = Node.Mode.NORMAL;
        this.authorizationStrategy = AuthorizationStrategy.UNSECURED;
        this.securityRealm = SecurityRealm.NO_AUTHENTICATION;
        this.projectNamingStrategy = ProjectNamingStrategy.DefaultProjectNamingStrategy.DEFAULT_NAMING_STRATEGY;
        this.workspaceDir = OLD_DEFAULT_WORKSPACES_DIR;
        this.buildsDir = DEFAULT_BUILDS_DIR;
        this.initLevel = InitMilestone.STARTED;
        this.items = new CopyOnWriteMap.Tree(String.CASE_INSENSITIVE_ORDER);
        this.jdks = new ArrayList();
        this.dependencyGraphLock = new Object();
        this.viewsTabBar = new DefaultViewsTabBar();
        this.myViewsTabBar = new DefaultMyViewsTabBar();
        this.extensionLists = new ConcurrentHashMap();
        this.descriptorLists = new ConcurrentHashMap();
        this.computers = new ConcurrentHashMap();
        this.clouds = new Hudson.CloudList(this);
        this.nodes = new Nodes(this);
        this.views = new CopyOnWriteArrayList<>();
        this.viewGroupMixIn = new ViewGroupMixIn(this) { // from class: jenkins.model.Jenkins.1
            protected List<View> views() {
                return Jenkins.this.views;
            }

            protected String primaryView() {
                return Jenkins.this.primaryView;
            }

            protected void primaryView(String name) {
                Jenkins.this.primaryView = name;
            }
        };
        this.fingerprintMap = new FingerprintMap();
        this.tcpSlaveAgentListenerLock = new Object();
        this.scmListeners = new CopyOnWriteList<>();
        this.slaveAgentPort = getSlaveAgentPortInitialValue(0);
        this.label = "";
        this.crumbIssuer = GlobalCrumbIssuerConfiguration.createDefaultCrumbIssuer();
        this.labels = new ConcurrentHashMap<>();
        this.overallLoad = new OverallLoadStatistics();
        this.unlabeledLoad = new UnlabeledLoadStatistics();
        this.unlabeledNodeProvisioner = new NodeProvisioner((Label) null, this.unlabeledLoad);
        this.overallNodeProvisioner = this.unlabeledNodeProvisioner;
        this.actions = new CopyOnWriteArrayList();
        this.nodeProperties = new DescribableList<>(this);
        this.globalNodeProperties = new DescribableList<>(this);
        this.administrativeMonitors = getExtensionList(AdministrativeMonitor.class);
        this.widgets = getExtensionList(Widget.class);
        this.itemGroupMixIn = new ItemGroupMixIn(this, this) { // from class: jenkins.model.Jenkins.2
            protected void add(TopLevelItem item) {
                Jenkins.this.items.put(item.getName(), item);
            }

            protected File getRootDirFor(String name) {
                return Jenkins.this.getRootDirFor(name);
            }
        };
        this.updateCenter = UpdateCenter.createUpdateCenter((UpdateCenter.UpdateCenterConfiguration) null);
        this.log = new LogRecorderManager();
        this.threadPoolForLoad = new ThreadPoolExecutor(TWICE_CPU_NUM, TWICE_CPU_NUM, 5L, TimeUnit.SECONDS, (BlockingQueue<Runnable>) new LinkedBlockingQueue(), (ThreadFactory) new NamingThreadFactory(new DaemonThreadFactory(), "Jenkins load"));
        this.oldJenkinsJVM = JenkinsJVM.isJenkinsJVM();
        JenkinsJVMAccess._setJenkinsJVM(true);
        long start = System.currentTimeMillis();
        STARTUP_MARKER_FILE = new FileBoolean(new File(root, ".lastStarted"));
        ACLContext ctx = ACL.as2(ACL.SYSTEM2);
        try {
            this.root = root;
            this.jakartaServletContext = context;
            this.servletContext = ServletContextWrapper.fromJakartServletContext(context);
            computeVersion(context);
            if (theInstance != null) {
                throw new IllegalStateException("second instance");
            }
            theInstance = this;
            if (!new File(root, "jobs").exists()) {
                this.workspaceDir = DEFAULT_WORKSPACES_DIR;
            }
            InitStrategy is = InitStrategy.get(Thread.currentThread().getContextClassLoader());
            Trigger.timer = new Timer("Jenkins cron thread");
            this.queue = new Queue(LoadBalancer.CONSISTENT_HASH);
            this.labelAtomSet = Collections.unmodifiableSet(Label.parse(this.label));
            try {
                this.dependencyGraph = DependencyGraph.EMPTY;
                TextFile secretFile = new TextFile(new File(getRootDir(), "secret.key"));
                if (secretFile.exists()) {
                    this.secretKey = secretFile.readTrim();
                } else {
                    byte[] random = new byte[32];
                    RANDOM.nextBytes(random);
                    this.secretKey = Util.toHexString(random);
                    secretFile.write(this.secretKey);
                    new FileBoolean(new File(root, "secret.key.not-so-secret")).on();
                }
                try {
                    this.proxy = ProxyConfiguration.load();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to load proxy configuration", (Throwable) e);
                }
                pluginManager = pluginManager == null ? PluginManager.createDefault(this) : pluginManager;
                this.pluginManager = pluginManager;
                WebApp webApp = WebApp.get(getServletContext());
                webApp.setClassLoader(pluginManager.uberClassLoader);
                webApp.setJsonInErrorMessageSanitizer(RedactSecretJsonInErrorMessageSanitizer.INSTANCE);
                TypedFilter typedFilter = new TypedFilter();
                webApp.setFilterForGetMethods(typedFilter);
                webApp.setFilterForFields(typedFilter);
                webApp.setFilterForDoActions(new DoActionFilter());
                StaplerFilteredActionListener actionListener = new StaplerFilteredActionListener();
                webApp.setFilteredGetterTriggerListener(actionListener);
                webApp.setFilteredDoActionTriggerListener(actionListener);
                webApp.setFilteredFieldTriggerListener(actionListener);
                webApp.setDispatchValidator(new StaplerDispatchValidator());
                webApp.setFilteredDispatchTriggerListener(actionListener);
                this.adjuncts = new AdjunctManager(getServletContext(), pluginManager.uberClassLoader, "adjuncts/" + SESSION_HASH, TimeUnit.DAYS.toMillis(365L));
                ClassFilterImpl.register();
                LOGGER.info("Starting version " + String.valueOf(getVersion()));
                ConfidentialStore.get();
                executeReactor(is, pluginManager.initTasks(is), loadTasks(), InitMilestone.ordering());
                if (this.initLevel != InitMilestone.COMPLETED) {
                    LOGGER.log(Level.SEVERE, "Jenkins initialization has not reached the COMPLETED initialization milestone after the startup. Current state: {0}. It may cause undefined incorrect behavior in Jenkins plugin relying on this state. It is likely an issue with the Initialization task graph. Example: usage of @Initializer(after = InitMilestone.COMPLETED) in a plugin (JENKINS-37759). Please create a bug in Jenkins bugtracker. ", this.initLevel);
                }
                if (KILL_AFTER_LOAD) {
                    System.exit(0);
                }
                save();
                launchTcpSlaveAgentListener();
                jenkins.util.Timer.get().scheduleAtFixedRate(new SafeTimerTask() { // from class: jenkins.model.Jenkins.4
                    protected void doRun() throws Exception {
                        Jenkins.this.trimLabels();
                    }
                }, TimeUnit.MINUTES.toMillis(5L), TimeUnit.MINUTES.toMillis(5L), TimeUnit.MILLISECONDS);
                updateComputerList();
                Computer c = toComputer();
                if (c != null) {
                    Iterator it = ComputerListener.all().iterator();
                    while (it.hasNext()) {
                        ComputerListener cl = (ComputerListener) it.next();
                        try {
                            cl.onOnline(c, new LogTaskListener(LOGGER, Level.INFO));
                        } catch (Exception e2) {
                            LOGGER.log(Level.WARNING, String.format("Exception in onOnline() for the computer listener %s on the built-in node", cl.getClass()), (Throwable) e2);
                        }
                    }
                }
                Iterator it2 = ItemListener.all().iterator();
                while (it2.hasNext()) {
                    ItemListener l = (ItemListener) it2.next();
                    long itemListenerStart = System.currentTimeMillis();
                    try {
                        l.onLoaded();
                    } catch (RuntimeException x) {
                        LOGGER.log(Level.WARNING, (String) null, (Throwable) x);
                    }
                    if (LOG_STARTUP_PERFORMANCE) {
                        LOGGER.info(String.format("Took %dms for item listener %s startup", Long.valueOf(System.currentTimeMillis() - itemListenerStart), l.getClass().getName()));
                    }
                }
                if (LOG_STARTUP_PERFORMANCE) {
                    LOGGER.info(String.format("Took %dms for complete Jenkins startup", Long.valueOf(System.currentTimeMillis() - start)));
                }
                STARTUP_MARKER_FILE.on();
                if (ctx != null) {
                    ctx.close();
                }
            } catch (InternalError e3) {
                if (e3.getMessage().contains("window server")) {
                    throw new Error("Looks like the server runs without X. Please specify -Djava.awt.headless=true as JVM option", e3);
                }
                throw e3;
            }
        } catch (Throwable th) {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    protected Object readResolve() {
        if (this.jdks == null) {
            this.jdks = new ArrayList();
        }
        if (SLAVE_AGENT_PORT_ENFORCE) {
            this.slaveAgentPort = getSlaveAgentPortInitialValue(this.slaveAgentPort);
        }
        this.installStateName = null;
        if (this.nodeRenameMigrationNeeded == null) {
            this.nodeRenameMigrationNeeded = true;
        }
        _setLabelString(this.label);
        return this;
    }

    @CheckForNull
    public ProxyConfiguration getProxy() {
        return this.proxy;
    }

    public void setProxy(@CheckForNull ProxyConfiguration proxy) {
        this.proxy = proxy;
    }

    @NonNull
    public InstallState getInstallState() {
        if (this.installState != null) {
            this.installStateName = this.installState.name();
            this.installState = null;
        }
        InstallState is = this.installStateName != null ? InstallState.valueOf(this.installStateName) : InstallState.UNKNOWN;
        return is != null ? is : InstallState.UNKNOWN;
    }

    public void setInstallState(@NonNull InstallState newState) {
        String prior = this.installStateName;
        this.installStateName = newState.name();
        LOGGER.log(Main.isDevelopmentMode ? Level.INFO : Level.FINE, "Install state transitioning from: {0} to: {1}", new Object[]{prior, this.installStateName});
        if (!this.installStateName.equals(prior)) {
            getSetupWizard().onInstallStateUpdate(newState);
            newState.initializeState();
        }
    }

    /* JADX WARN: Type inference failed for: r0v1, types: [jenkins.model.Jenkins$6] */
    private void executeReactor(final InitStrategy is, TaskBuilder... builders) throws IOException, InterruptedException, ReactorException {
        Reactor reactor = new Reactor(this, builders) { // from class: jenkins.model.Jenkins.5
            protected void runTask(Task task) throws Exception {
                if (is == null || !is.skipInitTask(task)) {
                    String taskName = InitReactorRunner.getDisplayName(task);
                    Thread t = Thread.currentThread();
                    String name = t.getName();
                    if (taskName != null) {
                        t.setName(taskName);
                    }
                    try {
                        try {
                            ACLContext ctx = ACL.as2(ACL.SYSTEM2);
                            try {
                                long start = System.currentTimeMillis();
                                super.runTask(task);
                                if (AbstractCIBase.LOG_STARTUP_PERFORMANCE) {
                                    Jenkins.LOGGER.info(String.format("Took %dms for %s by %s", Long.valueOf(System.currentTimeMillis() - start), taskName, name));
                                }
                                if (ctx != null) {
                                    ctx.close();
                                }
                                t.setName(name);
                            } catch (Throwable th) {
                                if (ctx != null) {
                                    try {
                                        ctx.close();
                                    } catch (Throwable th2) {
                                        th.addSuppressed(th2);
                                    }
                                }
                                throw th;
                            }
                        } catch (Error | Exception x) {
                            if (containsLinkageError(x)) {
                                Jenkins.LOGGER.log(Level.WARNING, taskName + " failed perhaps due to plugin dependency issues", x);
                                t.setName(name);
                                return;
                            }
                            throw x;
                        }
                    } catch (Throwable th3) {
                        t.setName(name);
                        throw th3;
                    }
                }
            }

            private boolean containsLinkageError(Throwable x) {
                if (x instanceof LinkageError) {
                    return true;
                }
                Throwable x2 = x.getCause();
                return x2 != null && containsLinkageError(x2);
            }
        };
        new InitReactorRunner() { // from class: jenkins.model.Jenkins.6
            protected void onInitMilestoneAttained(InitMilestone milestone) {
                Jenkins.this.initLevel = milestone;
                Jenkins.this.getLifecycle().onExtendTimeout(Jenkins.EXTEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (milestone == InitMilestone.PLUGINS_PREPARED) {
                    ExtensionList.lookup(ExtensionFinder.class).getComponents();
                }
            }
        }.run(reactor);
    }

    public TcpSlaveAgentListener getTcpSlaveAgentListener() {
        return this.tcpSlaveAgentListener;
    }

    public AdjunctManager getAdjuncts(String dummy) {
        return this.adjuncts;
    }

    @Exported
    public int getSlaveAgentPort() {
        return this.slaveAgentPort;
    }

    public boolean isSlaveAgentPortEnforced() {
        return SLAVE_AGENT_PORT_ENFORCE;
    }

    public void setSlaveAgentPort(int port) throws IOException {
        if (SLAVE_AGENT_PORT_ENFORCE) {
            LOGGER.log(Level.WARNING, "setSlaveAgentPort({0}) call ignored because system property {1} is true", (Object[]) new String[]{Integer.toString(port), Jenkins.class.getName() + ".slaveAgentPortEnforce"});
        } else {
            forceSetSlaveAgentPort(port);
        }
    }

    private void forceSetSlaveAgentPort(int port) throws IOException {
        this.slaveAgentPort = port;
        launchTcpSlaveAgentListener();
    }

    @NonNull
    public synchronized Set<String> getAgentProtocols() {
        return (Set) AgentProtocol.all().stream().map((v0) -> {
            return v0.getName();
        }).filter((v0) -> {
            return Objects.nonNull(v0);
        }).collect(Collectors.toCollection(TreeSet::new));
    }

    @Deprecated
    public synchronized void setAgentProtocols(@NonNull Set<String> protocols) {
        LOGGER.log(Level.WARNING, (String) null, (Throwable) new IllegalStateException("Jenkins.agentProtocols no longer configurable"));
    }

    private void launchTcpSlaveAgentListener() throws IOException {
        synchronized (this.tcpSlaveAgentListenerLock) {
            if (this.tcpSlaveAgentListener != null && this.tcpSlaveAgentListener.configuredPort != this.slaveAgentPort) {
                this.tcpSlaveAgentListener.shutdown();
                this.tcpSlaveAgentListener = null;
            }
            if (this.slaveAgentPort != -1 && this.tcpSlaveAgentListener == null) {
                String administrativeMonitorId = getClass().getName() + ".tcpBind";
                try {
                    this.tcpSlaveAgentListener = new TcpSlaveAgentListener(this.slaveAgentPort);
                    AdministrativeMonitor toBeRemoved = null;
                    ExtensionList<AdministrativeMonitor> all = AdministrativeMonitor.all();
                    Iterator it = all.iterator();
                    while (true) {
                        if (!it.hasNext()) {
                            break;
                        }
                        AdministrativeMonitor am = (AdministrativeMonitor) it.next();
                        if (administrativeMonitorId.equals(am.id)) {
                            toBeRemoved = am;
                            break;
                        }
                    }
                    all.remove(toBeRemoved);
                } catch (BindException e) {
                    LOGGER.log(Level.WARNING, String.format("Failed to listen to incoming agent connections through port %s. Change the port number", Integer.valueOf(this.slaveAgentPort)), (Throwable) e);
                    new AdministrativeError(administrativeMonitorId, "Failed to listen to incoming agent connections", "Failed to listen to incoming agent connections. <a href='configureSecurity'>Change the inbound TCP port number</a> to solve the problem.", e);
                }
            }
        }
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: Jenkins$EnforceSlaveAgentPortAdministrativeMonitor.class */
    public static class EnforceSlaveAgentPortAdministrativeMonitor extends AdministrativeMonitor {

        @Inject
        Jenkins j;

        public String getDisplayName() {
            return Messages.EnforceSlaveAgentPortAdministrativeMonitor_displayName();
        }

        public String getSystemPropertyName() {
            return Jenkins.class.getName() + ".slaveAgentPort";
        }

        public int getExpectedPort() {
            int slaveAgentPort = this.j.slaveAgentPort;
            return Jenkins.getSlaveAgentPortInitialValue(slaveAgentPort);
        }

        @RequirePOST
        public void doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            this.j.forceSetSlaveAgentPort(getExpectedPort());
            rsp.sendRedirect2(req.getContextPath() + "/manage");
        }

        public boolean isActivated() {
            int slaveAgentPort = Jenkins.get().slaveAgentPort;
            return Jenkins.SLAVE_AGENT_PORT_ENFORCE && slaveAgentPort != Jenkins.getSlaveAgentPortInitialValue(slaveAgentPort);
        }
    }

    public void setNodeName(String name) {
        throw new UnsupportedOperationException();
    }

    public String getNodeDescription() {
        return Messages.Hudson_NodeDescription();
    }

    @Exported
    public String getDescription() {
        return this.systemMessage;
    }

    @NonNull
    public PluginManager getPluginManager() {
        return this.pluginManager;
    }

    public UpdateCenter getUpdateCenter() {
        return this.updateCenter;
    }

    @CheckForNull
    public Boolean isNoUsageStatistics() {
        return this.noUsageStatistics;
    }

    public boolean isUsageStatisticsCollected() {
        return this.noUsageStatistics == null || !this.noUsageStatistics.booleanValue();
    }

    public void setNoUsageStatistics(Boolean noUsageStatistics) throws IOException {
        this.noUsageStatistics = noUsageStatistics;
        save();
    }

    public Api getApi() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            Object attribute = req.getAttribute("jakarta.servlet.error.message");
            if (attribute != null) {
                return null;
            }
        }
        return new Api(this);
    }

    @Deprecated
    public String getSecretKey() {
        return this.secretKey;
    }

    @Deprecated
    public SecretKey getSecretKeyAsAES128() {
        return Util.toAes128Key(this.secretKey);
    }

    public String getLegacyInstanceId() {
        return Util.getDigestOf(getSecretKey());
    }

    public Descriptor<SCM> getScm(String shortClassName) {
        return findDescriptor(shortClassName, SCM.all());
    }

    public Descriptor<RepositoryBrowser<?>> getRepositoryBrowser(String shortClassName) {
        return findDescriptor(shortClassName, RepositoryBrowser.all());
    }

    public Descriptor<Builder> getBuilder(String shortClassName) {
        return findDescriptor(shortClassName, Builder.all());
    }

    public Descriptor<BuildWrapper> getBuildWrapper(String shortClassName) {
        return findDescriptor(shortClassName, BuildWrapper.all());
    }

    public Descriptor<Publisher> getPublisher(String shortClassName) {
        return findDescriptor(shortClassName, Publisher.all());
    }

    public TriggerDescriptor getTrigger(String shortClassName) {
        return findDescriptor(shortClassName, Trigger.all());
    }

    public Descriptor<RetentionStrategy<?>> getRetentionStrategy(String shortClassName) {
        return findDescriptor(shortClassName, RetentionStrategy.all());
    }

    public JobPropertyDescriptor getJobProperty(String shortClassName) {
        return findDescriptor(shortClassName, JobPropertyDescriptor.all());
    }

    @Deprecated
    public ComputerSet getComputer() {
        return new ComputerSet();
    }

    @Restricted({DoNotUse.class})
    public CloudSet getCloud() {
        return new CloudSet();
    }

    public Descriptor getDescriptor(String id) {
        ExtensionList<Descriptor> extensionList = getExtensionList(Descriptor.class);
        for (Descriptor d : extensionList) {
            if (d.getId().equals(id)) {
                return d;
            }
        }
        Descriptor candidate = null;
        for (Descriptor d2 : extensionList) {
            String name = d2.getId();
            if (name.substring(name.lastIndexOf(46) + 1).equals(id)) {
                if (candidate == null) {
                    candidate = d2;
                } else {
                    throw new IllegalArgumentException(id + " is ambiguous; matches both " + name + " and " + candidate.getId());
                }
            }
        }
        return candidate;
    }

    public Descriptor getDescriptorByName(String id) {
        return getDescriptor(id);
    }

    @CheckForNull
    public Descriptor getDescriptor(Class<? extends Describable> type) {
        Iterator it = getExtensionList(Descriptor.class).iterator();
        while (it.hasNext()) {
            Descriptor d = (Descriptor) it.next();
            if (d.clazz == type) {
                return d;
            }
        }
        return null;
    }

    @NonNull
    public Descriptor getDescriptorOrDie(Class<? extends Describable> type) {
        Descriptor d = getDescriptor(type);
        if (d == null) {
            throw new AssertionError(String.valueOf(type) + " is missing its descriptor");
        }
        return d;
    }

    public <T extends Descriptor> T getDescriptorByType(Class<T> type) {
        Iterator it = getExtensionList(Descriptor.class).iterator();
        while (it.hasNext()) {
            Descriptor d = (Descriptor) it.next();
            if (d.getClass() == type) {
                return type.cast(d);
            }
        }
        return null;
    }

    public Descriptor<SecurityRealm> getSecurityRealms(String shortClassName) {
        return findDescriptor(shortClassName, SecurityRealm.all());
    }

    private <T extends Describable<T>> Descriptor<T> findDescriptor(String shortClassName, Collection<? extends Descriptor<T>> descriptors) {
        String name = "." + shortClassName;
        for (Descriptor<T> d : descriptors) {
            if (d.clazz.getName().endsWith(name)) {
                return d;
            }
        }
        return null;
    }

    protected void updateNewComputer(Node n) {
        updateNewComputer(n, AUTOMATIC_AGENT_LAUNCH);
    }

    protected void updateComputerList() {
        HashSet<Node> allNodes = new HashSet<>();
        allNodes.add(this);
        allNodes.addAll(getNodes());
        updateComputerList(AUTOMATIC_AGENT_LAUNCH, allNodes);
    }

    protected void updateComputers(@NonNull Node... nodes) {
        HashSet<Node> nodeSet = new HashSet<>();
        Collections.addAll(nodeSet, nodes);
        updateComputerList(AUTOMATIC_AGENT_LAUNCH, nodeSet);
    }

    @Deprecated
    public CopyOnWriteList<SCMListener> getSCMListeners() {
        return this.scmListeners;
    }

    @CheckForNull
    public Plugin getPlugin(String shortName) {
        PluginWrapper p = this.pluginManager.getPlugin(shortName);
        if (p == null) {
            return null;
        }
        return p.getPlugin();
    }

    @CheckForNull
    public <P extends Plugin> P getPlugin(Class<P> cls) {
        PluginWrapper plugin = this.pluginManager.getPlugin(cls);
        if (plugin == null) {
            return null;
        }
        return (P) plugin.getPlugin();
    }

    public <P extends Plugin> List<P> getPlugins(Class<P> clazz) {
        ArrayList arrayList = new ArrayList();
        for (PluginWrapper w : this.pluginManager.getPlugins(clazz)) {
            arrayList.add(w.getPlugin());
        }
        return Collections.unmodifiableList(arrayList);
    }

    public String getSystemMessage() {
        return this.systemMessage;
    }

    @NonNull
    public MarkupFormatter getMarkupFormatter() {
        MarkupFormatter f = this.markupFormatter;
        return f != null ? f : new EscapedMarkupFormatter();
    }

    public void setMarkupFormatter(MarkupFormatter f) {
        this.markupFormatter = f;
    }

    public void setSystemMessage(String message) throws IOException {
        this.systemMessage = message;
        save();
    }

    @StaplerDispatchable
    public FederatedLoginService getFederatedLoginService(String name) {
        Iterator it = FederatedLoginService.all().iterator();
        while (it.hasNext()) {
            FederatedLoginService fls = (FederatedLoginService) it.next();
            if (fls.getUrlName().equals(name)) {
                return fls;
            }
        }
        return null;
    }

    public List<FederatedLoginService> getFederatedLoginServices() {
        return FederatedLoginService.all();
    }

    /* JADX WARN: Multi-variable type inference failed */
    public Launcher createLauncher(TaskListener listener) {
        return new Launcher.LocalLauncher(listener).decorateFor(this);
    }

    @NonNull
    public String getFullName() {
        return "";
    }

    public String getFullDisplayName() {
        return "";
    }

    public List<Action> getActions() {
        return this.actions;
    }

    @Exported(name = "jobs")
    /* renamed from: getItems, reason: merged with bridge method [inline-methods] */
    public List<TopLevelItem> m105getItems() {
        return getItems(t -> {
            return true;
        });
    }

    public List<TopLevelItem> getItems(Predicate<TopLevelItem> pred) {
        List<TopLevelItem> viewableItems = new ArrayList<>();
        for (TopLevelItem item : this.items.values()) {
            if (pred.test(item) && item.hasPermission(Item.READ)) {
                viewableItems.add(item);
            }
        }
        return viewableItems;
    }

    public Map<String, TopLevelItem> getItemMap() {
        return Collections.unmodifiableMap(this.items);
    }

    public <T> List<T> getItems(Class<T> type) {
        List<T> r = new ArrayList<>();
        Objects.requireNonNull(type);
        for (TopLevelItem i : getItems((v1) -> {
            return r1.isInstance(v1);
        })) {
            r.add(type.cast(i));
        }
        return r;
    }

    @Deprecated
    public List<Project> getProjects() {
        return Util.createSubList(this.items.values(), Project.class);
    }

    public Collection<String> getJobNames() {
        List<String> names = new ArrayList<>();
        for (Job j : allItems(Job.class)) {
            names.add(j.getFullName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Restricted({NoExternalUse.class})
    public ComboBoxModel doFillJobNameItems() {
        return new ComboBoxModel(getJobNames());
    }

    public List<Action> getViewActions() {
        return getActions();
    }

    public Collection<String> getTopLevelItemNames() {
        List<String> names = new ArrayList<>();
        for (TopLevelItem j : this.items.values()) {
            names.add(j.getName());
        }
        return names;
    }

    @CheckForNull
    public View getView(@CheckForNull String name) {
        return this.viewGroupMixIn.getView(name);
    }

    @Exported
    public Collection<View> getViews() {
        return this.viewGroupMixIn.getViews();
    }

    public void addView(@NonNull View v) throws IOException {
        this.viewGroupMixIn.addView(v);
    }

    public void setViews(Collection<View> views) throws IOException {
        BulkChange bc = new BulkChange(this);
        try {
            this.views.clear();
            for (View v : views) {
                addView(v);
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

    public boolean canDelete(View view) {
        return this.viewGroupMixIn.canDelete(view);
    }

    public synchronized void deleteView(View view) throws IOException {
        this.viewGroupMixIn.deleteView(view);
    }

    public void onViewRenamed(View view, String oldName, String newName) {
        this.viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    @Exported
    public View getPrimaryView() {
        return this.viewGroupMixIn.getPrimaryView();
    }

    public void setPrimaryView(@NonNull View v) {
        this.primaryView = v.getViewName();
    }

    public ViewsTabBar getViewsTabBar() {
        return this.viewsTabBar;
    }

    public void setViewsTabBar(ViewsTabBar viewsTabBar) {
        this.viewsTabBar = viewsTabBar;
    }

    /* renamed from: getItemGroup, reason: merged with bridge method [inline-methods] */
    public Jenkins m107getItemGroup() {
        return this;
    }

    @Deprecated
    public MyViewsTabBar getMyViewsTabBar() {
        return this.myViewsTabBar;
    }

    @Deprecated
    public void setMyViewsTabBar(MyViewsTabBar myViewsTabBar) {
        this.myViewsTabBar = myViewsTabBar;
    }

    public boolean isUpgradedFromBefore(VersionNumber v) {
        try {
            return new VersionNumber(this.version).isOlderThan(v);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Computer[] getComputers() {
        return (Computer[]) getComputersCollection().stream().sorted(Comparator.comparing((v0) -> {
            return v0.getName();
        })).toArray(x$0 -> {
            return new Computer[x$0];
        });
    }

    @CheckForNull
    @CLIResolver
    public Computer getComputer(@Argument(required = true, metaVar = "NAME", usage = "Node name") @NonNull String name) {
        if (name.equals("(built-in)") || name.equals("(master)")) {
            name = "";
        }
        for (Computer c : getComputersCollection()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @CheckForNull
    public Label getLabel(String expr) {
        if (expr == null) {
            return null;
        }
        String expr2 = QuotedStringTokenizer.unquote(expr);
        while (true) {
            Label l = this.labels.get(expr2);
            if (l != null) {
                return l;
            }
            try {
                this.labels.putIfAbsent(expr2, Label.parseExpression(expr2));
            } catch (IllegalArgumentException e) {
                return getLabelAtom(expr2);
            }
        }
    }

    @Nullable
    public LabelAtom getLabelAtom(@CheckForNull String name) {
        if (name == null) {
            return null;
        }
        while (true) {
            LabelAtom labelAtom = (Label) this.labels.get(name);
            if (labelAtom != null) {
                return labelAtom;
            }
            Label labelAtom2 = new LabelAtom(name);
            if (this.labels.putIfAbsent(name, labelAtom2) == null) {
                labelAtom2.load();
            }
        }
    }

    @Restricted({NoExternalUse.class})
    @Nullable
    public LabelAtom tryGetLabelAtom(@NonNull String name) {
        LabelAtom labelAtom = (Label) this.labels.get(name);
        if (labelAtom instanceof LabelAtom) {
            return labelAtom;
        }
        return null;
    }

    public Set<Label> getLabels() {
        Set<Label> r = new TreeSet<>();
        for (Label l : this.labels.values()) {
            if (!l.isEmpty()) {
                r.add(l);
            }
        }
        return r;
    }

    protected Set<LabelAtom> getLabelAtomSet() {
        return this.labelAtomSet;
    }

    public Set<LabelAtom> getLabelAtoms() {
        Set<LabelAtom> r = new TreeSet<>();
        Iterator<Label> it = this.labels.values().iterator();
        while (it.hasNext()) {
            LabelAtom labelAtom = (Label) it.next();
            if (!labelAtom.isEmpty() && (labelAtom instanceof LabelAtom)) {
                r.add(labelAtom);
            }
        }
        return r;
    }

    public Queue getQueue() {
        return this.queue;
    }

    public String getDisplayName() {
        return Messages.Hudson_DisplayName();
    }

    public List<JDK> getJDKs() {
        return this.jdks;
    }

    @Restricted({NoExternalUse.class})
    public void setJDKs(Collection<? extends JDK> jdks) {
        this.jdks = new ArrayList(jdks);
    }

    public JDK getJDK(String name) {
        if (name == null) {
            List<JDK> jdks = getJDKs();
            if (jdks.size() == 1) {
                return (JDK) jdks.getFirst();
            }
            return null;
        }
        for (JDK j : getJDKs()) {
            if (j.getName().equals(name)) {
                return j;
            }
        }
        return null;
    }

    @CheckForNull
    public Node getNode(String name) {
        return this.nodes.getNode(name);
    }

    @CheckForNull
    @Restricted({Beta.class})
    public Node getOrLoadNode(String nodeName) {
        return getNodesObject().getOrLoad(nodeName);
    }

    public Cloud getCloud(String name) {
        return this.clouds.getByName(name);
    }

    protected ConcurrentMap<Node, Computer> getComputerMap() {
        return this.computers;
    }

    @Restricted({NoExternalUse.class})
    public Collection<Computer> getComputersCollection() {
        return this.computers.values();
    }

    @NonNull
    public List<Node> getNodes() {
        return this.nodes.getNodes();
    }

    @Restricted({NoExternalUse.class})
    public Nodes getNodesObject() {
        return this.nodes;
    }

    public void addNode(Node n) throws IOException {
        this.nodes.addNode(n);
    }

    public void removeNode(@NonNull Node n) throws IOException {
        this.nodes.removeNode(n);
    }

    @Restricted({Beta.class})
    public void unloadNode(@NonNull Node n) {
        this.nodes.unload(n);
    }

    public boolean updateNode(Node n) throws IOException {
        return this.nodes.updateNode(n);
    }

    public void setNodes(final List<? extends Node> n) throws IOException {
        this.nodes.setNodes(n);
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        return this.nodeProperties;
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getGlobalNodeProperties() {
        return this.globalNodeProperties;
    }

    void trimLabels() {
        trimLabels((Set<LabelAtom>) null);
    }

    void trimLabels(Node... nodes) {
        Set<LabelAtom> includedLabels = new HashSet<>();
        Arrays.stream(nodes).filter((v0) -> {
            return Objects.nonNull(v0);
        }).forEach(n -> {
            includedLabels.addAll(n.drainLabelsToTrim());
        });
        trimLabels(includedLabels);
    }

    private void trimLabels(@CheckForNull Set<LabelAtom> includedLabels) {
        Set<Set<LabelAtom>> nodeLabels = new HashSet<>();
        nodeLabels.add(getAssignedLabels());
        getNodes().forEach(n -> {
            nodeLabels.add(n.getAssignedLabels());
        });
        Iterator<Label> itr = this.labels.values().iterator();
        while (itr.hasNext()) {
            Label l = itr.next();
            if (includedLabels == null || includedLabels.contains(l) || l.matches(includedLabels)) {
                Stream<Set<LabelAtom>> stream = nodeLabels.stream();
                Objects.requireNonNull(l);
                if (stream.anyMatch((v1) -> {
                    return r1.matches(v1);
                }) || !l.getClouds().isEmpty()) {
                    resetLabel(l);
                } else {
                    itr.remove();
                }
            }
        }
    }

    @CheckForNull
    public AdministrativeMonitor getAdministrativeMonitor(String id) {
        for (AdministrativeMonitor m : this.administrativeMonitors) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public List<AdministrativeMonitor> getActiveAdministrativeMonitors() {
        if (!AdministrativeMonitor.hasPermissionToDisplay()) {
            return Collections.emptyList();
        }
        return (List) this.administrativeMonitors.stream().filter(m -> {
            try {
                if (m.hasRequiredPermission() && m.isEnabled()) {
                    if (m.isActivated()) {
                        return true;
                    }
                }
                return false;
            } catch (Throwable x) {
                LOGGER.log(Level.WARNING, (String) null, x);
                return false;
            }
        }).collect(Collectors.toList());
    }

    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public NodeDescriptor m108getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    /* loaded from: Jenkins$DescriptorImpl.class */
    public static final class DescriptorImpl extends NodeDescriptor {

        @Extension
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        public boolean isInstantiable() {
            return false;
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }

        public Object getDynamic(String token) {
            return Jenkins.get().getDescriptor(token);
        }
    }

    public int getQuietPeriod() {
        if (this.quietPeriod != null) {
            return this.quietPeriod.intValue();
        }
        return 5;
    }

    public void setQuietPeriod(Integer quietPeriod) throws IOException {
        this.quietPeriod = quietPeriod;
        save();
    }

    public int getScmCheckoutRetryCount() {
        return this.scmCheckoutRetryCount;
    }

    public void setScmCheckoutRetryCount(int scmCheckoutRetryCount) throws IOException {
        this.scmCheckoutRetryCount = scmCheckoutRetryCount;
        save();
    }

    public String getSearchUrl() {
        return "";
    }

    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder builder = super.makeSearchIndex();
        this.actions.stream().filter(e -> {
            return (e.getIconFileName() == null || e.getUrlName() == null) ? false : true;
        }).forEach(action -> {
            builder.add(new SearchItem(this) { // from class: jenkins.model.Jenkins.7
                public String getSearchName() {
                    return action.getDisplayName();
                }

                public String getSearchUrl() {
                    return action.getUrlName();
                }

                public String getSearchIcon() {
                    return action.getIconFileName();
                }

                public SearchIndex getSearchIndex() {
                    return SearchIndex.EMPTY;
                }
            });
        });
        builder.add(new CollectionSearchIndex<TopLevelItem>() { // from class: jenkins.model.Jenkins.11
            protected SearchItem get(String key) {
                return Jenkins.this.getItemByFullName(key, TopLevelItem.class);
            }

            protected Collection<TopLevelItem> all() {
                return Jenkins.this.getAllItems(TopLevelItem.class);
            }

            @NonNull
            protected Iterable<TopLevelItem> allAsIterable() {
                return Jenkins.this.allItems(TopLevelItem.class);
            }
        }).add(getPrimaryView().makeSearchIndex()).add(new CollectionSearchIndex() { // from class: jenkins.model.Jenkins.10
            /* JADX INFO: Access modifiers changed from: protected */
            /* renamed from: get, reason: merged with bridge method [inline-methods] */
            public Computer m110get(String key) {
                return Jenkins.this.getComputer(key);
            }

            protected Collection<Computer> all() {
                return Jenkins.this.getComputersCollection();
            }
        }).add(new CollectionSearchIndex(this) { // from class: jenkins.model.Jenkins.9
            /* JADX INFO: Access modifiers changed from: protected */
            /* renamed from: get, reason: merged with bridge method [inline-methods] */
            public User m112get(String key) {
                return User.get(key, false);
            }

            protected Collection<User> all() {
                return User.getAll();
            }
        }).add(new CollectionSearchIndex() { // from class: jenkins.model.Jenkins.8
            /* JADX INFO: Access modifiers changed from: protected */
            /* renamed from: get, reason: merged with bridge method [inline-methods] */
            public View m111get(String key) {
                return Jenkins.this.getView(key);
            }

            protected Collection<View> all() {
                return Jenkins.this.getAllViews();
            }
        });
        return builder;
    }

    public String getUrlChildPrefix() {
        return "job";
    }

    @Nullable
    public String getRootUrl() throws IllegalStateException {
        JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        String url = config.getUrl();
        if (url != null) {
            return Util.ensureEndsWith(url, "/");
        }
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            return getRootUrlFromRequest();
        }
        return null;
    }

    @Exported(name = "url")
    @CheckForNull
    @Restricted({DoNotUse.class})
    public String getConfiguredRootUrl() {
        JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        return config.getUrl();
    }

    public boolean isRootUrlSecure() {
        String url = getRootUrl();
        return url != null && url.startsWith("https");
    }

    @NonNull
    public String getRootUrlFromRequest() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            throw new IllegalStateException("cannot call getRootUrlFromRequest from outside a request handling thread");
        }
        StringBuilder buf = new StringBuilder();
        String scheme = getXForwardedHeader(req, "X-Forwarded-Proto", req.getScheme());
        buf.append(scheme).append("://");
        String host = getXForwardedHeader(req, "X-Forwarded-Host", req.getServerName());
        int index = host.lastIndexOf(58);
        int port = req.getServerPort();
        if (index == -1) {
            buf.append(host);
        } else if (host.startsWith("[") && host.endsWith("]")) {
            buf.append(host);
        } else {
            buf.append((CharSequence) host, 0, index);
            if (index + 1 < host.length()) {
                try {
                    port = Integer.parseInt(host.substring(index + 1));
                } catch (NumberFormatException e) {
                }
            }
        }
        String forwardedPort = getXForwardedHeader(req, "X-Forwarded-Port", null);
        if (forwardedPort != null) {
            try {
                port = Integer.parseInt(forwardedPort);
            } catch (NumberFormatException e2) {
            }
        }
        if (port != ("https".equals(scheme) ? 443 : 80)) {
            buf.append(':').append(port);
        }
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    private static String getXForwardedHeader(StaplerRequest2 req, String header, String defaultValue) {
        String value = req.getHeader(header);
        if (value != null) {
            int index = value.indexOf(44);
            return index == -1 ? value.trim() : value.substring(0, index).trim();
        }
        return defaultValue;
    }

    public File getRootDir() {
        return this.root;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public FilePath getWorkspaceFor(TopLevelItem item) {
        Iterator it = WorkspaceLocator.all().iterator();
        while (it.hasNext()) {
            WorkspaceLocator l = (WorkspaceLocator) it.next();
            FilePath workspace = l.locate(item, this);
            if (workspace != null) {
                return workspace;
            }
        }
        return new FilePath(expandVariablesForDirectory(this.workspaceDir, item));
    }

    public File getBuildDirFor(Job job) {
        return expandVariablesForDirectory(this.buildsDir, job);
    }

    @Restricted({NoExternalUse.class})
    public boolean isDefaultBuildDir() {
        return DEFAULT_BUILDS_DIR.equals(this.buildsDir);
    }

    @Restricted({NoExternalUse.class})
    boolean isDefaultWorkspaceDir() {
        return OLD_DEFAULT_WORKSPACES_DIR.equals(this.workspaceDir) || DEFAULT_WORKSPACES_DIR.equals(this.workspaceDir);
    }

    private File expandVariablesForDirectory(String base, Item item) {
        return new File(expandVariablesForDirectory(base, item.getFullName(), item.getRootDir().getPath()));
    }

    @Restricted({NoExternalUse.class})
    public static String expandVariablesForDirectory(String base, String itemFullName, String itemRootDir) {
        Map<String, String> properties = new HashMap<>();
        properties.put("JENKINS_HOME", get().getRootDir().getPath());
        properties.put("ITEM_ROOTDIR", itemRootDir);
        properties.put("ITEM_FULLNAME", itemFullName);
        properties.put("ITEM_FULL_NAME", itemFullName.replace(':', '$'));
        return Util.replaceMacro(base, Collections.unmodifiableMap(properties));
    }

    public String getRawWorkspaceDir() {
        return this.workspaceDir;
    }

    public String getRawBuildsDir() {
        return this.buildsDir;
    }

    @Restricted({NoExternalUse.class})
    public void setRawBuildsDir(String buildsDir) {
        this.buildsDir = buildsDir;
    }

    @NonNull
    public FilePath getRootPath() {
        return new FilePath(getRootDir());
    }

    public FilePath createPath(String absolutePath) {
        return new FilePath((VirtualChannel) null, absolutePath);
    }

    public ClockDifference getClockDifference() {
        return ClockDifference.ZERO;
    }

    public Callable<ClockDifference, IOException> getClockDifferenceCallable() {
        return new ClockDifferenceCallable();
    }

    /* loaded from: Jenkins$ClockDifferenceCallable.class */
    private static class ClockDifferenceCallable extends MasterToSlaveCallable<ClockDifference, IOException> {
        private ClockDifferenceCallable() {
        }

        /* renamed from: call, reason: merged with bridge method [inline-methods] */
        public ClockDifference m113call() throws IOException {
            return new ClockDifference(0L);
        }
    }

    public LogRecorderManager getLog() {
        checkPermission(SYSTEM_READ);
        return this.log;
    }

    public void setLog(LogRecorderManager log) {
        checkPermission(ADMINISTER);
        this.log = log;
    }

    @Exported
    public boolean isUseSecurity() {
        return (this.securityRealm == SecurityRealm.NO_AUTHENTICATION && this.authorizationStrategy == AuthorizationStrategy.UNSECURED) ? false : true;
    }

    public boolean isUseProjectNamingStrategy() {
        return this.projectNamingStrategy != ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY;
    }

    @Exported
    public boolean isUseCrumbs() {
        return this.crumbIssuer != null;
    }

    public SecurityMode getSecurity() {
        SecurityRealm realm = this.securityRealm;
        if (realm == SecurityRealm.NO_AUTHENTICATION) {
            return SecurityMode.UNSECURED;
        }
        if (realm instanceof LegacySecurityRealm) {
            return SecurityMode.LEGACY;
        }
        return SecurityMode.SECURED;
    }

    public SecurityRealm getSecurityRealm() {
        return this.securityRealm;
    }

    public void setSecurityRealm(@CheckForNull SecurityRealm securityRealm) {
        IdStrategy userIdStrategy;
        if (securityRealm == null) {
            securityRealm = SecurityRealm.NO_AUTHENTICATION;
        }
        this.useSecurity = true;
        if (this.securityRealm == null) {
            userIdStrategy = securityRealm.getUserIdStrategy();
        } else {
            userIdStrategy = this.securityRealm.getUserIdStrategy();
        }
        IdStrategy oldUserIdStrategy = userIdStrategy;
        this.securityRealm = securityRealm;
        resetFilter(securityRealm, oldUserIdStrategy);
        saveQuietly();
    }

    private void resetFilter(@CheckForNull SecurityRealm securityRealm, @CheckForNull IdStrategy oldUserIdStrategy) {
        try {
            HudsonFilter filter = HudsonFilter.get(getServletContext());
            if (filter == null) {
                LOGGER.fine("HudsonFilter has not yet been initialized: Can't perform security setup for now");
            } else {
                LOGGER.fine("HudsonFilter has been previously initialized: Setting security up");
                filter.reset(securityRealm);
                LOGGER.fine("Security is now fully set up");
            }
            if (oldUserIdStrategy != null && this.securityRealm != null && !oldUserIdStrategy.equals(this.securityRealm.getUserIdStrategy())) {
                User.rekey();
            }
        } catch (ServletException e) {
            throw new RuntimeException("Failed to configure filter", e) { // from class: jenkins.model.Jenkins.12
            };
        }
    }

    public void setAuthorizationStrategy(@CheckForNull AuthorizationStrategy a) {
        if (a == null) {
            a = AuthorizationStrategy.UNSECURED;
        }
        this.useSecurity = true;
        this.authorizationStrategy = a;
        saveQuietly();
    }

    public boolean isDisableRememberMe() {
        return this.disableRememberMe;
    }

    public void setDisableRememberMe(boolean disableRememberMe) {
        this.disableRememberMe = disableRememberMe;
    }

    public void disableSecurity() {
        this.useSecurity = null;
        setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        this.authorizationStrategy = AuthorizationStrategy.UNSECURED;
    }

    public void setProjectNamingStrategy(ProjectNamingStrategy ns) {
        if (ns == null) {
            ns = ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY;
        }
        this.projectNamingStrategy = ns;
    }

    public Lifecycle getLifecycle() {
        return Lifecycle.get();
    }

    @CheckForNull
    public Injector getInjector() {
        return (Injector) lookup(Injector.class);
    }

    public <T> ExtensionList<T> getExtensionList(Class<T> extensionType) {
        ExtensionList<T> extensionList = this.extensionLists.get(extensionType);
        return extensionList != null ? extensionList : this.extensionLists.computeIfAbsent(extensionType, key -> {
            return ExtensionList.create(this, key);
        });
    }

    @Deprecated(since = "2.519")
    public ExtensionList getExtensionList(String extensionType) throws ClassNotFoundException {
        return getExtensionList(this.pluginManager.uberClassLoader.loadClass(extensionType));
    }

    @NonNull
    public <T extends Describable<T>, D extends Descriptor<T>> DescriptorExtensionList<T, D> getDescriptorList(Class<T> type) {
        return this.descriptorLists.computeIfAbsent(type, key -> {
            return DescriptorExtensionList.createDescriptorList(this, key);
        });
    }

    public void refreshExtensions() throws ExtensionRefreshException {
        ExtensionList<ExtensionFinder> finders = getExtensionList(ExtensionFinder.class);
        LOGGER.finer(() -> {
            return "refreshExtensions " + String.valueOf(finders);
        });
        Iterator it = finders.iterator();
        while (it.hasNext()) {
            ExtensionFinder ef = (ExtensionFinder) it.next();
            if (!ef.isRefreshable()) {
                throw new ExtensionRefreshException(String.valueOf(ef) + " doesn't support refresh");
            }
        }
        List<ExtensionComponentSet> fragments = new ArrayList<>();
        Iterator it2 = finders.iterator();
        while (it2.hasNext()) {
            ExtensionFinder ef2 = (ExtensionFinder) it2.next();
            LOGGER.finer(() -> {
                return "searching " + String.valueOf(ef2);
            });
            fragments.add(ef2.refresh());
        }
        ExtensionComponentSet delta = ExtensionComponentSet.union(fragments).filtered();
        List<ExtensionComponent<ExtensionFinder>> newFinders = new ArrayList<>((Collection<? extends ExtensionComponent<ExtensionFinder>>) delta.find(ExtensionFinder.class));
        while (!newFinders.isEmpty()) {
            ExtensionFinder f = (ExtensionFinder) ((ExtensionComponent) newFinders.removeLast()).getInstance();
            LOGGER.finer(() -> {
                return "found new ExtensionFinder " + String.valueOf(f);
            });
            ExtensionComponentSet ecs = ExtensionComponentSet.allOf(f).filtered();
            newFinders.addAll(ecs.find(ExtensionFinder.class));
            delta = ExtensionComponentSet.union(new ExtensionComponentSet[]{delta, ecs});
        }
        Iterator it3 = finders.iterator();
        while (it3.hasNext()) {
            ExtensionFinder ef3 = (ExtensionFinder) it3.next();
            LOGGER.finer(() -> {
                return "searching again in " + String.valueOf(ef3);
            });
            delta = ExtensionComponentSet.union(new ExtensionComponentSet[]{delta, ef3.refresh().filtered()});
        }
        List<ExtensionList> listsToFireOnChangeListeners = new ArrayList<>();
        for (ExtensionList el : this.extensionLists.values()) {
            if (el.refresh(delta)) {
                listsToFireOnChangeListeners.add(el);
            }
        }
        for (ExtensionList el2 : this.descriptorLists.values()) {
            if (el2.refresh(delta)) {
                listsToFireOnChangeListeners.add(el2);
            }
        }
        Iterator<ExtensionList> it4 = listsToFireOnChangeListeners.iterator();
        while (it4.hasNext()) {
            it4.next().fireOnChangeListeners();
        }
        for (ExtensionComponent<RootAction> ea : delta.find(RootAction.class)) {
            Action a = (Action) ea.getInstance();
            if (!this.actions.contains(a)) {
                this.actions.add(a);
            }
        }
    }

    @NonNull
    public ACL getACL() {
        return this.authorizationStrategy.getRootACL();
    }

    public AuthorizationStrategy getAuthorizationStrategy() {
        return this.authorizationStrategy;
    }

    public ProjectNamingStrategy getProjectNamingStrategy() {
        return this.projectNamingStrategy == null ? ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY : this.projectNamingStrategy;
    }

    @Exported
    public boolean isQuietingDown() {
        return this.quietDownInfo != null;
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public boolean isPreparingSafeRestart() {
        QuietDownInfo quietDownInfo = this.quietDownInfo;
        if (quietDownInfo != null) {
            return quietDownInfo.isSafeRestart();
        }
        return false;
    }

    @Exported
    @CheckForNull
    public String getQuietDownReason() {
        QuietDownInfo info = this.quietDownInfo;
        if (info != null) {
            return info.message;
        }
        return null;
    }

    public boolean isTerminating() {
        return this.terminating;
    }

    public InitMilestone getInitLevel() {
        return this.initLevel;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void setNumExecutors(int n) throws IOException, IllegalArgumentException {
        if (n < 0) {
            throw new IllegalArgumentException("Incorrect field \"# of executors\": " + n + ". It should be a non-negative number.");
        }
        if (this.numExecutors != n) {
            this.numExecutors = n;
            updateComputers(this);
            save();
        }
    }

    /* renamed from: getItem, reason: merged with bridge method [inline-methods] */
    public TopLevelItem m103getItem(String name) throws AccessDeniedException {
        TopLevelItem item;
        if (name == null || (item = this.items.get(name)) == null) {
            return null;
        }
        if (!item.hasPermission(Item.READ)) {
            if (item.hasPermission(Item.DISCOVER)) {
                throw new AccessDeniedException("Please login to access job " + name);
            }
            return null;
        }
        return item;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public Item getItem(String str, ItemGroup itemGroup) {
        Jenkins jenkins2 = itemGroup;
        if (itemGroup == 0) {
            jenkins2 = this;
        }
        if (str == null) {
            return null;
        }
        if (str.startsWith("/")) {
            return getItemByFullName(str);
        }
        Jenkins jenkins3 = jenkins2;
        StringTokenizer stringTokenizer = new StringTokenizer(str, "/");
        while (true) {
            if (!stringTokenizer.hasMoreTokens()) {
                break;
            }
            String nextToken = stringTokenizer.nextToken();
            if (nextToken.equals("..")) {
                if (jenkins3 instanceof Item) {
                    jenkins3 = ((Item) jenkins3).getParent();
                } else {
                    jenkins3 = null;
                    break;
                }
            } else if (nextToken.equals(".")) {
                continue;
            } else if (jenkins3 instanceof ItemGroup) {
                Jenkins item = ((ItemGroup) jenkins3).getItem(nextToken);
                if (item == null || !item.hasPermission(Item.READ)) {
                    break;
                }
                jenkins3 = item;
            } else {
                return null;
            }
        }
        jenkins3 = null;
        if (jenkins3 instanceof Item) {
            return (Item) jenkins3;
        }
        return getItemByFullName(str);
    }

    public final Item getItem(String pathName, Item context) {
        return getItem(pathName, context != null ? context.getParent() : null);
    }

    public final <T extends Item> T getItem(String pathName, ItemGroup context, @NonNull Class<T> type) {
        Item r = getItem(pathName, context);
        if (type.isInstance(r)) {
            return type.cast(r);
        }
        return null;
    }

    public final <T extends Item> T getItem(String str, Item item, Class<T> cls) {
        return (T) getItem(str, item != null ? item.getParent() : null, cls);
    }

    public File getRootDirFor(TopLevelItem child) {
        return getRootDirFor(child.getName());
    }

    private File getRootDirFor(String name) {
        return new File(new File(getRootDir(), "jobs"), name);
    }

    @CheckForNull
    public <T extends Item> T getItemByFullName(@NonNull String fullName, Class<T> type) throws AccessDeniedException {
        StringTokenizer tokens = new StringTokenizer(fullName, "/");
        Jenkins jenkins2 = this;
        if (!tokens.hasMoreTokens()) {
            return null;
        }
        while (true) {
            Item item = jenkins2.getItem(tokens.nextToken());
            if (!tokens.hasMoreTokens()) {
                if (type.isInstance(item)) {
                    return type.cast(item);
                }
                return null;
            }
            if (!(item instanceof ItemGroup) || !item.hasPermission(Item.READ)) {
                return null;
            }
            jenkins2 = (ItemGroup) item;
        }
    }

    @CheckForNull
    public Item getItemByFullName(String fullName) {
        return getItemByFullName(fullName, Item.class);
    }

    @CheckForNull
    public User getUser(String name) {
        return User.get(name, User.ALLOW_USER_CREATION_VIA_URL && hasPermission(ADMINISTER));
    }

    @NonNull
    public synchronized TopLevelItem createProject(@NonNull TopLevelItemDescriptor type, @NonNull String name) throws IOException {
        return createProject(type, name, true);
    }

    @NonNull
    public synchronized TopLevelItem createProject(@NonNull TopLevelItemDescriptor type, @NonNull String name, boolean notify) throws IOException {
        return this.itemGroupMixIn.createProject(type, name, notify);
    }

    public synchronized void putItem(TopLevelItem item) throws IOException, InterruptedException {
        String name = item.getName();
        TopLevelItem old = this.items.get(name);
        if (old == item) {
            return;
        }
        checkPermission(Item.CREATE);
        if (old != null) {
            old.delete();
        }
        this.items.put(name, item);
        ItemListener.fireOnCreated(item);
    }

    @NonNull
    public synchronized <T extends TopLevelItem> T createProject(@NonNull Class<T> type, @NonNull String name) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptor) getDescriptorOrDie(type), name));
    }

    public void onRenamed(TopLevelItem job, String oldName, String newName) throws IOException {
        this.items.remove(oldName);
        this.items.put(newName, job);
        Iterator<View> it = this.views.iterator();
        while (it.hasNext()) {
            View v = it.next();
            v.onJobRenamed(job, oldName, newName);
        }
    }

    public void onDeleted(TopLevelItem item) throws IOException {
        ItemListener.fireOnDeleted(item);
        this.items.remove(item.getName());
        Iterator<View> it = this.views.iterator();
        while (it.hasNext()) {
            View v = it.next();
            v.onJobRenamed(item, item.getName(), (String) null);
        }
    }

    public boolean canAdd(TopLevelItem item) {
        return true;
    }

    public synchronized <I extends TopLevelItem> I add(I item, String name) throws IOException, IllegalArgumentException {
        if (this.items.containsKey(name)) {
            throw new IllegalArgumentException("already an item '" + name + "'");
        }
        this.items.put(name, item);
        return item;
    }

    public void remove(TopLevelItem item) throws IOException, IllegalArgumentException {
        this.items.remove(item.getName());
    }

    public FingerprintMap getFingerprintMap() {
        return this.fingerprintMap;
    }

    @StaplerDispatchable
    public Object getFingerprint(String md5sum) throws IOException {
        Fingerprint r = (Fingerprint) this.fingerprintMap.get(md5sum);
        return r == null ? new NoFingerprintMatch(md5sum) : r;
    }

    public Fingerprint _getFingerprint(String md5sum) throws IOException {
        return (Fingerprint) this.fingerprintMap.get(md5sum);
    }

    @Restricted({NoExternalUse.class})
    protected XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(this.root, "config.xml"));
    }

    public int getNumExecutors() {
        return this.numExecutors;
    }

    public Node.Mode getMode() {
        return this.mode;
    }

    public void setMode(Node.Mode m) throws IOException {
        this.mode = m;
        save();
    }

    public String getLabelString() {
        return Util.fixNull(this.label).trim();
    }

    public void setLabelString(String label) throws IOException {
        _setLabelString(label);
        save();
    }

    private void _setLabelString(String label) {
        this.label = label;
        if (getInstanceOrNull() != null) {
            this.labelAtomSet = Collections.unmodifiableSet(Label.parse(label));
        }
    }

    @NonNull
    public LabelAtom getSelfLabel() {
        if (nodeNameAndSelfLabelOverride != null) {
            return getLabelAtom(nodeNameAndSelfLabelOverride);
        }
        if (getRenameMigrationDone()) {
            return getLabelAtom("built-in");
        }
        return getLabelAtom("master");
    }

    boolean getRenameMigrationDone() {
        return this.nodeRenameMigrationNeeded == null || !this.nodeRenameMigrationNeeded.booleanValue();
    }

    void performRenameMigration() throws IOException {
        this.nodeRenameMigrationNeeded = false;
        save();
        trimLabels();
    }

    @NonNull
    public Computer createComputer() {
        return new Hudson.MasterComputer();
    }

    /* JADX WARN: Multi-variable type inference failed */
    public void load() throws IOException {
        XmlFile cfg = getConfigFile();
        if (cfg.exists()) {
            String originalPrimaryView = this.primaryView;
            ArrayList originalViews = new ArrayList(this.views);
            this.primaryView = null;
            this.views.clear();
            try {
                cfg.unmarshal(this);
            } catch (IOException | RuntimeException x) {
                this.primaryView = originalPrimaryView;
                this.views.clear();
                this.views.addAll(originalViews);
                throw x;
            }
        }
        if (this.views.isEmpty() || this.primaryView == null) {
            AllView allView = new AllView("all");
            setViewOwner(allView);
            this.views.addFirst(allView);
            this.primaryView = allView.getViewName();
        }
        this.primaryView = AllView.migrateLegacyPrimaryAllViewLocalizedName(this.views, this.primaryView);
        this.clouds.setOwner(this);
        this.configLoaded = true;
        try {
            checkRawBuildsDir(this.buildsDir);
            setBuildsAndWorkspacesDir();
            resetFilter(this.securityRealm, null);
            updateComputers(this);
        } catch (InvalidBuildsDir invalidBuildsDir) {
            throw new IOException((Throwable) invalidBuildsDir);
        }
    }

    private void setBuildsAndWorkspacesDir() throws IOException, InvalidBuildsDir {
        boolean mustSave = false;
        String newBuildsDir = SystemProperties.getString(BUILDS_DIR_PROP);
        boolean freshStartup = STARTUP_MARKER_FILE.isOff();
        if (newBuildsDir != null && !this.buildsDir.equals(newBuildsDir)) {
            checkRawBuildsDir(newBuildsDir);
            Level level = freshStartup ? Level.INFO : Level.WARNING;
            LOGGER.log(level, "Changing builds directories from {0} to {1}. Beware that no automated data migration will occur.", (Object[]) new String[]{this.buildsDir, newBuildsDir});
            this.buildsDir = newBuildsDir;
            mustSave = true;
        } else if (!isDefaultBuildDir()) {
            LOGGER.log(Level.INFO, "Using non default builds directories: {0}.", this.buildsDir);
        }
        String newWorkspacesDir = SystemProperties.getString(WORKSPACES_DIR_PROP);
        if (newWorkspacesDir != null && !this.workspaceDir.equals(newWorkspacesDir)) {
            Level level2 = freshStartup ? Level.INFO : Level.WARNING;
            LOGGER.log(level2, "Changing workspaces directories from {0} to {1}. Beware that no automated data migration will occur.", (Object[]) new String[]{this.workspaceDir, newWorkspacesDir});
            this.workspaceDir = newWorkspacesDir;
            mustSave = true;
        } else if (!isDefaultWorkspaceDir()) {
            LOGGER.log(Level.INFO, "Using non default workspaces directories: {0}.", this.workspaceDir);
        }
        if (mustSave) {
            save();
        }
    }

    @VisibleForTesting
    static void checkRawBuildsDir(String newBuildsDirValue) throws InvalidBuildsDir {
        String replacedValue = expandVariablesForDirectory(newBuildsDirValue, "doCheckRawBuildsDir-Marker:foo", get().getRootDir().getPath() + "/jobs/doCheckRawBuildsDir-Marker$foo");
        File replacedFile = new File(replacedValue);
        if (!replacedFile.isAbsolute()) {
            throw new InvalidBuildsDir(newBuildsDirValue + " does not resolve to an absolute path");
        }
        if (!replacedValue.contains("doCheckRawBuildsDir-Marker")) {
            throw new InvalidBuildsDir(newBuildsDirValue + " does not contain ${ITEM_FULL_NAME} or ${ITEM_ROOTDIR}, cannot distinguish between projects");
        }
        if (replacedValue.contains("doCheckRawBuildsDir-Marker:foo")) {
            try {
                File tmp = File.createTempFile("Jenkins-doCheckRawBuildsDir", "foo:bar");
                Files.delete(tmp.toPath());
            } catch (IOException | InvalidPathException e) {
                throw new InvalidBuildsDir(newBuildsDirValue + " contains ${ITEM_FULLNAME} but your system does not support it (JENKINS-12251). Use ${ITEM_FULL_NAME} instead").initCause(e);
            }
        }
        File d = new File(replacedValue);
        if (!d.isDirectory()) {
            do {
                d = d.getParentFile();
            } while (!d.exists());
            if (!d.canWrite()) {
                throw new InvalidBuildsDir(newBuildsDirValue + " does not exist and probably cannot be created");
            }
        }
    }

    private synchronized TaskBuilder loadTasks() throws IOException {
        File projectsDir = new File(this.root, "jobs");
        if (!projectsDir.getCanonicalFile().isDirectory() && !projectsDir.mkdirs()) {
            if (projectsDir.exists()) {
                throw new IOException(String.valueOf(projectsDir) + " is not a directory");
            }
            throw new IOException("Unable to create " + String.valueOf(projectsDir) + "\nPermission issue? Please create this directory manually.");
        }
        File[] subdirs = projectsDir.listFiles();
        Set<String> loadedNames = Collections.synchronizedSet(new HashSet());
        TaskGraphBuilder g = new TaskGraphBuilder();
        Milestone add = g.requires(new Milestone[]{InitMilestone.EXTENSIONS_AUGMENTED}).attains(new Milestone[]{InitMilestone.SYSTEM_CONFIG_LOADED}).add("Loading global config", session -> {
            load();
            if (this.slaves != null && !this.slaves.isEmpty() && this.nodes.isLegacy()) {
                this.nodes.setNodes(this.slaves);
                this.slaves = null;
            } else {
                this.nodes.load();
            }
        });
        List<TaskGraphBuilder.Handle> loadJobs = new ArrayList<>();
        for (File subdir : subdirs) {
            loadJobs.add(g.requires(new Milestone[]{add}).requires(new Milestone[]{InitMilestone.SYSTEM_CONFIG_ADAPTED}).attains(new Milestone[]{InitMilestone.JOB_LOADED}).notFatal().add("Loading item " + subdir.getName(), session2 -> {
                if (!Items.getConfigFile(subdir).exists()) {
                    return;
                }
                TopLevelItem item = Items.load(this, subdir);
                this.items.put(item.getName(), item);
                loadedNames.add(item.getName());
            }));
        }
        g.requires((Milestone[]) loadJobs.toArray(new TaskGraphBuilder.Handle[0])).attains(new Milestone[]{InitMilestone.JOB_LOADED}).add("Cleaning up obsolete items deleted from the disk", reactor -> {
            for (String name : this.items.keySet()) {
                if (!loadedNames.contains(name)) {
                    this.items.remove(name);
                }
            }
        });
        g.requires(new Milestone[]{InitMilestone.JOB_CONFIG_ADAPTED}).attains(new Milestone[]{InitMilestone.COMPLETED}).add("Finalizing set up", session3 -> {
            rebuildDependencyGraph();
            for (Node slave : this.nodes.getNodes()) {
                slave.getAssignedLabels();
            }
            getAssignedLabels();
            if (this.useSecurity != null && !this.useSecurity.booleanValue()) {
                this.authorizationStrategy = AuthorizationStrategy.UNSECURED;
                setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
            } else {
                if (this.authorizationStrategy == null) {
                    if (this.useSecurity == null) {
                        this.authorizationStrategy = AuthorizationStrategy.UNSECURED;
                    } else {
                        this.authorizationStrategy = new LegacyAuthorizationStrategy();
                    }
                }
                if (this.securityRealm == null) {
                    if (this.useSecurity == null) {
                        setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
                    } else {
                        setSecurityRealm(new LegacySecurityRealm());
                    }
                }
            }
            setCrumbIssuer(getCrumbIssuer());
            Iterator it = getExtensionList(RootAction.class).iterator();
            while (it.hasNext()) {
                Action a = (Action) it.next();
                if (!this.actions.contains(a)) {
                    this.actions.add(a);
                }
            }
            this.setupWizard = (SetupWizard) ExtensionList.lookupSingleton(SetupWizard.class);
            getInstallState().initializeState();
        });
        return g;
    }

    public synchronized void save() throws IOException {
        InitMilestone currentMilestone = this.initLevel;
        if (!this.configLoaded) {
            LOGGER.log(Level.SEVERE, "An attempt to save Jenkins'' global configuration before it has been loaded has been made during milestone " + String.valueOf(currentMilestone) + ".  This is indicative of a bug in the caller and may lead to full or partial loss of configuration.", (Throwable) new IllegalStateException("call trace"));
            throw new IllegalStateException("An attempt to save the global configuration was made before it was loaded");
        }
        if (BulkChange.contains(this)) {
            return;
        }
        if (currentMilestone == InitMilestone.COMPLETED) {
            LOGGER.log(Level.FINE, "setting version {0} to {1}", new Object[]{this.version, VERSION});
            this.version = VERSION;
        } else {
            LOGGER.log(Level.FINE, "refusing to set version {0} to {1} during {2}", new Object[]{this.version, VERSION, currentMilestone});
        }
        if (this.nodeRenameMigrationNeeded == null) {
            this.nodeRenameMigrationNeeded = false;
        }
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, (String) null, (Throwable) x);
        }
    }

    public void cleanUp() {
        if (theInstance != this && theInstance != null) {
            LOGGER.log(Level.WARNING, "This instance is no longer the singleton, ignoring cleanUp()");
            return;
        }
        synchronized (Jenkins.class) {
            if (this.cleanUpStarted) {
                LOGGER.log(Level.WARNING, "Jenkins.cleanUp() already started, ignoring repeated cleanUp()");
                return;
            }
            this.cleanUpStarted = true;
            try {
                getLifecycle().onStatusUpdate("Stopping Jenkins");
                List<Throwable> errors = new ArrayList<>();
                fireBeforeShutdown(errors);
                _cleanUpRunTerminators(errors);
                this.terminating = true;
                Set<Future<?>> pending = _cleanUpDisconnectComputers(errors);
                _cleanUpCancelDependencyGraphCalculation();
                _cleanUpInterruptReloadThread(errors);
                _cleanUpShutdownTriggers(errors);
                _cleanUpShutdownTimer(errors);
                _cleanUpShutdownTcpSlaveAgent(errors);
                _cleanUpShutdownPluginManager(errors);
                _cleanUpPersistQueue(errors);
                _cleanUpShutdownThreadPoolForLoad(errors);
                _cleanUpAwaitDisconnects(errors, pending);
                _cleanUpPluginServletFilters(errors);
                _cleanUpReleaseAllLoggers(errors);
                getLifecycle().onStatusUpdate("Jenkins stopped");
                if (!errors.isEmpty()) {
                    StringBuilder message = new StringBuilder("Unexpected issues encountered during cleanUp: ");
                    Iterator<Throwable> iterator = errors.iterator();
                    message.append(iterator.next().getMessage());
                    while (iterator.hasNext()) {
                        message.append("; ");
                        message.append(iterator.next().getMessage());
                    }
                    Iterator<Throwable> iterator2 = errors.iterator();
                    RuntimeException exception = new RuntimeException(message.toString(), iterator2.next());
                    while (iterator2.hasNext()) {
                        exception.addSuppressed(iterator2.next());
                    }
                    throw exception;
                }
            } finally {
                theInstance = null;
                if (JenkinsJVM.isJenkinsJVM()) {
                    JenkinsJVMAccess._setJenkinsJVM(this.oldJenkinsJVM);
                }
                ClassFilterImpl.unregister();
            }
        }
    }

    private void fireBeforeShutdown(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Notifying termination");
        Iterator it = ItemListener.all().iterator();
        while (it.hasNext()) {
            ItemListener l = (ItemListener) it.next();
            try {
                l.onBeforeShutdown();
            } catch (LinkageError e) {
                LOGGER.log(Level.WARNING, e, () -> {
                    return "ItemListener " + String.valueOf(l) + ": " + e.getMessage();
                });
            } catch (OutOfMemoryError e2) {
                throw e2;
            } catch (Throwable e3) {
                LOGGER.log(Level.WARNING, e3, () -> {
                    return "ItemListener " + String.valueOf(l) + ": " + e3.getMessage();
                });
                errors.add(e3);
            }
        }
    }

    private void _cleanUpRunTerminators(List<Throwable> errors) {
        try {
            new Reactor(new TaskBuilder[]{new TerminatorFinder(this.pluginManager != null ? this.pluginManager.uberClassLoader : Thread.currentThread().getContextClassLoader())}).execute((v0) -> {
                v0.run();
            }, new ReactorListener(this) { // from class: jenkins.model.Jenkins.13
                final Level level = Level.parse(SystemProperties.getString(Jenkins.class.getName() + ".termLogLevel", "FINE"));

                public void onTaskStarted(Task t) {
                    Jenkins.LOGGER.log(this.level, "Started {0}", InitReactorRunner.getDisplayName(t));
                }

                public void onTaskCompleted(Task t) {
                    Jenkins.LOGGER.log(this.level, "Completed {0}", InitReactorRunner.getDisplayName(t));
                }

                public void onTaskFailed(Task t, Throwable err, boolean fatal) {
                    Jenkins.LOGGER.log(Level.SEVERE, err, () -> {
                        return "Failed " + InitReactorRunner.getDisplayName(t);
                    });
                }

                public void onAttained(Milestone milestone) {
                    Level lv = this.level;
                    String s = "Attained " + milestone.toString();
                    if ((milestone instanceof TermMilestone) && !Main.isUnitTest) {
                        lv = Level.INFO;
                        s = milestone.toString();
                    }
                    Jenkins.LOGGER.log(lv, s);
                }
            });
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to execute termination", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (Throwable e3) {
            LOGGER.log(Level.SEVERE, "Failed to execute termination", e3);
            errors.add(e3);
        }
    }

    private Set<Future<?>> _cleanUpDisconnectComputers(final List<Throwable> errors) {
        LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Starting node disconnection");
        Set<Future<?>> pending = new HashSet<>();
        Queue.withLock(() -> {
            Iterator<Computer> it = getComputersCollection().iterator();
            while (it.hasNext()) {
                SlaveComputer slaveComputer = (Computer) it.next();
                try {
                    slaveComputer.interrupt();
                    slaveComputer.setNumExecutors(0);
                    if (Main.isUnitTest && (slaveComputer instanceof SlaveComputer)) {
                        SlaveComputer sc = slaveComputer;
                        sc.closeLog();
                    }
                    pending.add(slaveComputer.disconnect((OfflineCause) null));
                } catch (LinkageError e) {
                    LOGGER.log(Level.WARNING, e, () -> {
                        return "Could not disconnect " + String.valueOf(slaveComputer) + ": " + e.getMessage();
                    });
                } catch (OutOfMemoryError e2) {
                    throw e2;
                } catch (Throwable e3) {
                    LOGGER.log(Level.WARNING, e3, () -> {
                        return "Could not disconnect " + String.valueOf(slaveComputer) + ": " + e3.getMessage();
                    });
                    errors.add(e3);
                }
            }
        });
        return pending;
    }

    private void _cleanUpInterruptReloadThread(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Interrupting reload thread");
        try {
            interruptReloadThread();
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to interrupt reload thread", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (SecurityException e3) {
            LOGGER.log(Level.WARNING, "Not permitted to interrupt reload thread", (Throwable) e3);
            errors.add(e3);
        } catch (Throwable e4) {
            LOGGER.log(Level.SEVERE, "Failed to interrupt reload thread", e4);
            errors.add(e4);
        }
    }

    private void _cleanUpShutdownTriggers(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Shutting down triggers");
        try {
            final Timer timer = Trigger.timer;
            if (timer != null) {
                final CountDownLatch latch = new CountDownLatch(1);
                timer.schedule(new TimerTask(this) { // from class: jenkins.model.Jenkins.14
                    @Override // java.util.TimerTask, java.lang.Runnable
                    public void run() {
                        timer.cancel();
                        latch.countDown();
                    }
                }, 0L);
                if (latch.await(10L, TimeUnit.SECONDS)) {
                    LOGGER.log(Level.FINE, "Triggers shut down successfully");
                } else {
                    timer.cancel();
                    LOGGER.log(Level.INFO, "Gave up waiting for triggers to finish running");
                }
            }
            Trigger.timer = null;
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to shut down triggers", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (Throwable e3) {
            LOGGER.log(Level.SEVERE, "Failed to shut down triggers", e3);
            errors.add(e3);
        }
    }

    private void _cleanUpShutdownTimer(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Shutting down timer");
        try {
            jenkins.util.Timer.shutdown();
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to shut down Timer", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (SecurityException e3) {
            LOGGER.log(Level.WARNING, "Not permitted to shut down Timer", (Throwable) e3);
            errors.add(e3);
        } catch (Throwable e4) {
            LOGGER.log(Level.SEVERE, "Failed to shut down Timer", e4);
            errors.add(e4);
        }
    }

    private void _cleanUpShutdownTcpSlaveAgent(List<Throwable> errors) {
        if (this.tcpSlaveAgentListener != null) {
            LOGGER.log(Level.FINE, "Shutting down TCP/IP agent listener");
            try {
                this.tcpSlaveAgentListener.shutdown();
            } catch (LinkageError e) {
                LOGGER.log(Level.SEVERE, "Failed to shut down TCP/IP agent listener", (Throwable) e);
            } catch (OutOfMemoryError e2) {
                throw e2;
            } catch (Throwable e3) {
                LOGGER.log(Level.SEVERE, "Failed to shut down TCP/IP agent listener", e3);
                errors.add(e3);
            }
        }
    }

    private void _cleanUpShutdownPluginManager(List<Throwable> errors) {
        if (this.pluginManager != null) {
            LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Stopping plugin manager");
            try {
                this.pluginManager.stop();
            } catch (LinkageError e) {
                LOGGER.log(Level.SEVERE, "Failed to stop plugin manager", (Throwable) e);
            } catch (OutOfMemoryError e2) {
                throw e2;
            } catch (Throwable e3) {
                LOGGER.log(Level.SEVERE, "Failed to stop plugin manager", e3);
                errors.add(e3);
            }
        }
    }

    private void _cleanUpPersistQueue(List<Throwable> errors) {
        if (getRootDir().exists()) {
            LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Persisting build queue");
            try {
                getQueue().save();
            } catch (LinkageError e) {
                LOGGER.log(Level.SEVERE, "Failed to persist build queue", (Throwable) e);
            } catch (OutOfMemoryError e2) {
                throw e2;
            } catch (Throwable e3) {
                LOGGER.log(Level.SEVERE, "Failed to persist build queue", e3);
                errors.add(e3);
            }
        }
    }

    private void _cleanUpShutdownThreadPoolForLoad(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Shutting down Jenkins load thread pool");
        try {
            this.threadPoolForLoad.shutdown();
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to shut down Jenkins load thread pool", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (SecurityException e3) {
            LOGGER.log(Level.WARNING, "Not permitted to shut down Jenkins load thread pool", (Throwable) e3);
            errors.add(e3);
        } catch (Throwable e4) {
            LOGGER.log(Level.SEVERE, "Failed to shut down Jenkins load thread pool", e4);
            errors.add(e4);
        }
    }

    private void _cleanUpAwaitDisconnects(List<Throwable> errors, Set<Future<?>> pending) {
        long remaining;
        if (!pending.isEmpty()) {
            LOGGER.log(Main.isUnitTest ? Level.FINE : Level.INFO, "Waiting for node disconnection completion");
        }
        long end = System.nanoTime() + Duration.ofSeconds(10L).toNanos();
        for (Future<?> f : pending) {
            try {
                remaining = end - System.nanoTime();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (LinkageError e2) {
                LOGGER.log(Level.WARNING, "Failed to shut down remote computer connection", (Throwable) e2);
            } catch (OutOfMemoryError e3) {
                throw e3;
            } catch (ExecutionException e4) {
                LOGGER.log(Level.WARNING, "Failed to shut down remote computer connection cleanly", (Throwable) e4);
            } catch (TimeoutException e5) {
                LOGGER.log(Level.WARNING, "Failed to shut down remote computer connection within 10 seconds", (Throwable) e5);
            } catch (Throwable e6) {
                LOGGER.log(Level.SEVERE, "Unexpected error while waiting for remote computer connection disconnect", e6);
                errors.add(e6);
            }
            if (remaining <= 0) {
                LOGGER.warning("Ran out of time waiting for agents to disconnect");
                return;
            }
            f.get(remaining, TimeUnit.NANOSECONDS);
        }
    }

    private void _cleanUpPluginServletFilters(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Stopping filters");
        try {
            PluginServletFilter.cleanUp();
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to stop filters", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (Throwable e3) {
            LOGGER.log(Level.SEVERE, "Failed to stop filters", e3);
            errors.add(e3);
        }
    }

    private void _cleanUpReleaseAllLoggers(List<Throwable> errors) {
        LOGGER.log(Level.FINE, "Releasing all loggers");
        try {
            LogFactory.releaseAll();
        } catch (LinkageError e) {
            LOGGER.log(Level.SEVERE, "Failed to release all loggers", (Throwable) e);
        } catch (OutOfMemoryError e2) {
            throw e2;
        } catch (Throwable e3) {
            LOGGER.log(Level.SEVERE, "Failed to release all loggers", e3);
            errors.add(e3);
        }
    }

    private void _cleanUpCancelDependencyGraphCalculation() {
        synchronized (this.dependencyGraphLock) {
            LOGGER.log(Level.FINE, "Canceling internal dependency graph calculation");
            if (this.scheduledFutureDependencyGraph != null && !this.scheduledFutureDependencyGraph.isDone()) {
                this.scheduledFutureDependencyGraph.cancel(true);
            }
            if (this.calculatingFutureDependencyGraph != null && !this.calculatingFutureDependencyGraph.isDone()) {
                this.calculatingFutureDependencyGraph.cancel(true);
            }
        }
    }

    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            String url = a.getUrlName();
            if (url != null && (url.equals(token) || url.equals("/" + token))) {
                return a;
            }
        }
        for (Action a2 : getManagementLinks()) {
            if (Objects.equals(a2.getUrlName(), token)) {
                return a2;
            }
        }
        return null;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @POST
    public synchronized void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        BulkChange bc = new BulkChange(this);
        try {
            checkPermission(MANAGE);
            JSONObject json = req.getSubmittedForm();
            this.systemMessage = Util.nullify(req.getParameter("system_message"));
            boolean result = true;
            for (Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfigUnclassified()) {
                result &= configureDescriptor(req, json, d);
            }
            save();
            updateComputers(this);
            if (result) {
                FormApply.success(req.getContextPath() + "/").generateResponse(req, rsp, (Object) null);
            } else {
                FormApply.success("configure").generateResponse(req, rsp, (Object) null);
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

    @CheckForNull
    public CrumbIssuer getCrumbIssuer() {
        if (GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION) {
            return null;
        }
        return this.crumbIssuer;
    }

    public void setCrumbIssuer(CrumbIssuer issuer) {
        this.crumbIssuer = issuer;
    }

    public synchronized void doTestPost(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.sendRedirect("foo");
    }

    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d) throws Descriptor.FormException {
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject();
        json.putAll(js);
        return d.configure(req, js);
    }

    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    @RequirePOST
    public synchronized HttpRedirect doQuietDown() {
        try {
            return doQuietDown(false, 0, null);
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Deprecated
    public synchronized HttpRedirect doQuietDown(boolean block, int timeout) {
        try {
            return doQuietDown(block, timeout, null);
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Deprecated(since = "2.414")
    public HttpRedirect doQuietDown(boolean block, int timeout, @CheckForNull String message) throws InterruptedException, IOException {
        return doQuietDown(block, timeout, message, false);
    }

    @RequirePOST
    public HttpRedirect doQuietDown(@QueryParameter boolean block, @QueryParameter int timeout, @CheckForNull @QueryParameter String message, @QueryParameter boolean safeRestart) throws InterruptedException, IOException {
        synchronized (this) {
            checkPermission(MANAGE);
            this.quietDownInfo = new QuietDownInfo(message, safeRestart);
        }
        if (block) {
            long waitUntil = timeout;
            if (timeout > 0) {
                waitUntil += System.currentTimeMillis();
            }
            while (isQuietingDown() && ((timeout <= 0 || System.currentTimeMillis() < waitUntil) && !RestartListener.isAllReady())) {
                TimeUnit.SECONDS.sleep(1L);
            }
        }
        return new HttpRedirect(".");
    }

    @RequirePOST
    public synchronized HttpRedirect doCancelQuietDown() {
        checkPermission(MANAGE);
        this.quietDownInfo = null;
        getQueue().m44scheduleMaintenance();
        return new HttpRedirect(".");
    }

    @POST
    public HttpResponse doToggleCollapse() throws ServletException, IOException {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        String paneId = request.getParameter("paneId");
        PaneStatusProperties.forCurrentUser().toggleCollapsed(paneId);
        return HttpResponses.forwardToPreviousPage();
    }

    public void doClassicThreadDump(StaplerResponse2 rsp) throws IOException, ServletException {
        rsp.sendRedirect2("threadDump");
    }

    public Map<String, Map<String, String>> getAllThreadDumps() throws IOException, InterruptedException {
        checkPermission(ADMINISTER);
        Map<String, Future<Map<String, String>>> future = new HashMap<>();
        for (Computer c : getComputers()) {
            try {
                future.put(c.getName(), RemotingDiagnostics.getThreadDumpAsync(c.getChannel()));
            } catch (Exception e) {
                LOGGER.info("Failed to get thread dump for node " + c.getName() + ": " + e.getMessage());
            }
        }
        if (toComputer() == null) {
            future.put("master", RemotingDiagnostics.getThreadDumpAsync(FilePath.localChannel));
        }
        long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5L);
        Map<String, Map<String, String>> r = new HashMap<>();
        for (Map.Entry<String, Future<Map<String, String>>> e2 : future.entrySet()) {
            try {
                r.put(e2.getKey(), e2.getValue().get(endTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS));
            } catch (Exception x) {
                r.put(e2.getKey(), Map.of("Failed to retrieve thread dump", Functions.printThrowable(x)));
            }
        }
        return Collections.unmodifiableSortedMap(new TreeMap(r));
    }

    @RequirePOST
    /* renamed from: doCreateItem, reason: merged with bridge method [inline-methods] */
    public synchronized TopLevelItem m109doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        return this.itemGroupMixIn.createTopLevelItem(req, rsp);
    }

    public TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        return this.itemGroupMixIn.createProjectFromXML(name, xml);
    }

    public <T extends TopLevelItem> T copy(T t, String str) throws IOException {
        return (T) this.itemGroupMixIn.copy(t, str);
    }

    public <T extends AbstractProject<?, ?>> T copy(T src, String name) throws IOException {
        return copy((Jenkins) src, name);
    }

    @POST
    public synchronized void doCreateView(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(View.CREATE);
        addView(View.create(req, rsp, this));
    }

    public static void checkGoodName(String name) throws Failure {
        if (name == null || name.isEmpty()) {
            throw new Failure(Messages.Hudson_NoName());
        }
        if (".".equals(name.trim())) {
            throw new Failure(Messages.Jenkins_NotAllowedName("."));
        }
        if ("..".equals(name.trim())) {
            throw new Failure(Messages.Jenkins_NotAllowedName(".."));
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new Failure(Messages.Hudson_ControlCodeNotAllowed(toPrintableName(name)));
            }
            if ("?*/\\%!@#$^&|<>[]:;".indexOf(ch) != -1) {
                throw new Failure(Messages.Hudson_UnsafeChar(Character.valueOf(ch)));
            }
        }
        if (SystemProperties.getBoolean(NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP, true) && name.trim().endsWith(".")) {
            throw new Failure(Messages.Hudson_TrailingDot());
        }
    }

    private static String toPrintableName(String name) {
        StringBuilder printableName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch)) {
                printableName.append("\\u").append((int) ch).append(';');
            } else {
                printableName.append(ch);
            }
        }
        return printableName.toString();
    }

    public void doSecured(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (req.getUserPrincipal() == null) {
            rsp.setStatus(401);
            return;
        }
        String path = req.getContextPath() + req.getOriginalRestOfPath();
        String q = req.getQueryString();
        if (q != null) {
            path = path + "?" + q;
        }
        rsp.sendRedirect2(path);
    }

    public void doLoginEntry(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.getUserPrincipal() == null) {
            rsp.sendRedirect2("noPrincipal");
            return;
        }
        String from = req.getParameter("from");
        if (from != null && Util.isSafeToRedirectTo(from)) {
            rsp.sendRedirect2(from);
        } else {
            rsp.sendRedirect2(".");
        }
    }

    public void doLogout(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String user = getAuthentication2().getName();
        this.securityRealm.doLogout(req, rsp);
        SecurityListener.fireLoggedOut(user);
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doLogout(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        StaplerRequest2 staplerRequest2;
        if (req != null) {
            try {
                staplerRequest2 = StaplerRequest.toStaplerRequest2(req);
            } catch (ServletException e) {
                throw ServletExceptionWrapper.fromJakartaServletException(e);
            }
        } else {
            staplerRequest2 = null;
        }
        doLogout(staplerRequest2, rsp != null ? StaplerResponse.toStaplerResponse2(rsp) : null);
    }

    public Slave.JnlpJar getJnlpJars(String fileName) {
        return new Slave.JnlpJar(fileName);
    }

    public Slave.JnlpJar doJnlpJars(StaplerRequest2 req) {
        return new Slave.JnlpJar(req.getRestOfPath().substring(1));
    }

    /* JADX WARN: Type inference failed for: r0v6, types: [jenkins.model.Jenkins$15] */
    @RequirePOST
    public synchronized HttpResponse doReload() throws IOException {
        checkPermission(MANAGE);
        getLifecycle().onReload(getAuthentication2().getName(), (String) null);
        WebApp.get(getServletContext()).setApp(new HudsonIsLoading());
        new Thread("Jenkins config reload thread") { // from class: jenkins.model.Jenkins.15
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                try {
                    ACLContext ctx = ACL.as2(ACL.SYSTEM2);
                    try {
                        Jenkins.this.reload();
                        Jenkins.this.getLifecycle().onReady();
                        if (ctx != null) {
                            ctx.close();
                        }
                    } finally {
                    }
                } catch (Exception e) {
                    Jenkins.LOGGER.log(Level.SEVERE, "Failed to reload Jenkins config", (Throwable) e);
                    new JenkinsReloadFailed(e).publish(Jenkins.this.getServletContext(), Jenkins.this.root);
                }
            }
        }.start();
        return HttpResponses.redirectViaContextPath("/");
    }

    public void reload() throws IOException, InterruptedException, ReactorException {
        this.queue.save();
        executeReactor(null, loadTasks());
        if (this.initLevel != InitMilestone.COMPLETED) {
            LOGGER.log(Level.SEVERE, "Jenkins initialization has not reached the COMPLETED initialization milestone after the configuration reload. Current state: {0}. It may cause undefined incorrect behavior in Jenkins plugin relying on this state. It is likely an issue with the Initialization task graph. Example: usage of @Initializer(after = InitMilestone.COMPLETED) in a plugin (JENKINS-37759). Please create a bug in Jenkins bugtracker.", this.initLevel);
        }
        User.reload();
        this.queue.load();
        WebApp.get(getServletContext()).setApp(this);
    }

    @RequirePOST
    public void doDoFingerprintCheck(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        MultipartFormDataParser p = new MultipartFormDataParser(req, 10);
        try {
            if (isUseCrumbs() && !getCrumbIssuer().validateCrumb((ServletRequest) req, p)) {
                rsp.sendError(403, "No crumb found");
            }
            rsp.sendRedirect2(req.getContextPath() + "/fingerprint/" + Util.getDigestOf(p.getFileItem2("name").getInputStream()) + "/");
            p.close();
        } catch (Throwable th) {
            try {
                p.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    @RequirePOST
    @SuppressFBWarnings(value = {"DM_GC"}, justification = "for debugging")
    public void doGc(StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        System.gc();
        rsp.setStatus(JenkinsLocationConfiguration.ORDINAL);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("GCed");
    }

    public ModelObjectWithContextMenu.ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws IOException, JellyException {
        ModelObjectWithContextMenu.ContextMenu menu = new ModelObjectWithContextMenu.ContextMenu().from(this, request, response);
        for (ModelObjectWithContextMenu.MenuItem i : menu.items) {
            if (i.url.equals(request.getContextPath() + "/manage")) {
                i.subMenu = new ModelObjectWithContextMenu.ContextMenu().from((ModelObjectWithContextMenu) ExtensionList.lookupSingleton(ManageJenkinsAction.class), request, response, "index");
            }
        }
        return menu;
    }

    public ModelObjectWithContextMenu.ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ModelObjectWithContextMenu.ContextMenu menu = new ModelObjectWithContextMenu.ContextMenu();
        for (View view : getViews()) {
            menu.add(view.getViewUrl(), view.getDisplayName());
        }
        return menu;
    }

    public RemotingDiagnostics.HeapDump getHeapDump() throws IOException {
        return new RemotingDiagnostics.HeapDump(this, FilePath.localChannel);
    }

    @RequirePOST
    public void doSimulateOutOfMemory() throws IOException {
        checkPermission(ADMINISTER);
        System.out.println("Creating artificial OutOfMemoryError situation");
        List<Object> args = new ArrayList<>();
        while (true) {
            args.add(new byte[1048576]);
        }
    }

    public DirectoryBrowserSupport doUserContent() {
        return new DirectoryBrowserSupport(this, getRootPath().child("userContent"), "User content", "folder.png", true);
    }

    @CLIMethod(name = "restart")
    public void doRestart(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(MANAGE);
        if (req != null && req.getMethod().equals("GET")) {
            req.getView(this, "_restart.jelly").forward(req, rsp);
            return;
        }
        if (req == null || req.getMethod().equals("POST")) {
            restart();
        }
        if (rsp != null) {
            rsp.sendRedirect2(".");
        }
    }

    @WebMethod(name = {"404"})
    @Restricted({NoExternalUse.class})
    public void generateNotFoundResponse(StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
        if (ResourceDomainConfiguration.isResourceRequest(req)) {
            rsp.forward(this, "_404_simple", req);
            return;
        }
        Object attribute = req.getAttribute("jenkins.ErrorAttributeFilter.user");
        if (attribute instanceof Authentication) {
            ACLContext unused = ACL.as2((Authentication) attribute);
            try {
                rsp.forward(this, "_404", req);
                if (unused != null) {
                    unused.close();
                    return;
                }
                return;
            } catch (Throwable th) {
                if (unused != null) {
                    try {
                        unused.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        rsp.forward(this, "_404", req);
    }

    @Deprecated(since = "2.414")
    public HttpResponse doSafeRestart(StaplerRequest req) throws IOException, ServletException, RestartNotSupportedException {
        return doSafeRestart(req != null ? StaplerRequest.toStaplerRequest2(req) : null, (String) null);
    }

    public HttpResponse doSafeRestart(StaplerRequest2 req, @QueryParameter("message") String message) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(MANAGE);
        if (req != null && req.getMethod().equals("GET")) {
            return HttpResponses.forwardToView(this, "_safeRestart.jelly");
        }
        if (req != null && req.getParameter("cancel") != null) {
            return doCancelQuietDown();
        }
        if (req == null || req.getMethod().equals("POST")) {
            safeRestart(message);
        }
        return HttpResponses.redirectToDot();
    }

    @StaplerNotDispatchable
    @Deprecated
    public HttpResponse doSafeRestart(StaplerRequest req, @QueryParameter("message") String message) throws IOException, javax.servlet.ServletException, RestartNotSupportedException {
        StaplerRequest2 staplerRequest2;
        if (req != null) {
            try {
                staplerRequest2 = StaplerRequest.toStaplerRequest2(req);
            } catch (ServletException e) {
                throw ServletExceptionWrapper.fromJakartaServletException(e);
            }
        } else {
            staplerRequest2 = null;
        }
        return doSafeRestart(staplerRequest2, message);
    }

    private static Lifecycle restartableLifecycle() throws RestartNotSupportedException {
        if (Main.isUnitTest) {
            throw new RestartNotSupportedException("Restarting the controller JVM is not supported in JenkinsRule-based tests");
        }
        Lifecycle lifecycle = Lifecycle.get();
        lifecycle.verifyRestartable();
        return lifecycle;
    }

    /* JADX WARN: Type inference failed for: r0v3, types: [jenkins.model.Jenkins$16] */
    public void restart() throws RestartNotSupportedException {
        final Lifecycle lifecycle = restartableLifecycle();
        getServletContext().setAttribute("app", new HudsonIsRestarting());
        new Thread(this, "restart thread") { // from class: jenkins.model.Jenkins.16
            final String exitUser = Jenkins.getAuthentication2().getName();

            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                try {
                    ACLContext ctx = ACL.as2(ACL.SYSTEM2);
                    try {
                        lifecycle.onStatusUpdate("Restart in 5 seconds");
                        Thread.sleep(TimeUnit.SECONDS.toMillis(5L));
                        lifecycle.onStop(this.exitUser, (String) null);
                        Listeners.notify(RestartListener.class, true, (v0) -> {
                            v0.onRestart();
                        });
                        lifecycle.restart();
                        if (ctx != null) {
                            ctx.close();
                        }
                    } finally {
                    }
                } catch (InterruptedIOException | InterruptedException e) {
                    Jenkins.LOGGER.log(Level.WARNING, "Interrupted while trying to restart Jenkins", (Throwable) e);
                    Thread.currentThread().interrupt();
                } catch (IOException e2) {
                    Jenkins.LOGGER.log(Level.WARNING, "Failed to restart Jenkins", (Throwable) e2);
                }
            }
        }.start();
    }

    @Deprecated(since = "2.414")
    public void safeRestart() throws RestartNotSupportedException {
        safeRestart(null);
    }

    /* JADX WARN: Type inference failed for: r0v2, types: [jenkins.model.Jenkins$17] */
    public void safeRestart(final String message) throws RestartNotSupportedException {
        final Lifecycle lifecycle = restartableLifecycle();
        this.quietDownInfo = new QuietDownInfo(message, true);
        new Thread("safe-restart thread") { // from class: jenkins.model.Jenkins.17
            final String exitUser = Jenkins.getAuthentication2().getName();

            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                try {
                    ACLContext ctx = ACL.as2(ACL.SYSTEM2);
                    try {
                        Jenkins.this.doQuietDown(true, 0, message, true);
                        if (Jenkins.this.isQuietingDown()) {
                            Jenkins.this.getServletContext().setAttribute("app", new HudsonIsRestarting(true));
                            lifecycle.onStatusUpdate("Restart in 10 seconds");
                            Thread.sleep(TimeUnit.SECONDS.toMillis(10L));
                            lifecycle.onStop(this.exitUser, (String) null);
                            Listeners.notify(RestartListener.class, true, (v0) -> {
                                v0.onRestart();
                            });
                            lifecycle.restart();
                        } else {
                            lifecycle.onStatusUpdate("Safe-restart mode cancelled");
                        }
                        if (ctx != null) {
                            ctx.close();
                        }
                    } finally {
                    }
                } catch (Throwable e) {
                    Jenkins.LOGGER.log(Level.WARNING, "Failed to restart Jenkins", e);
                }
            }
        }.start();
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: Jenkins$MasterRestartNotifyier.class */
    public static class MasterRestartNotifyier extends RestartListener {
        public void onRestart() {
            Computer computer = Jenkins.get().toComputer();
            if (computer == null) {
                return;
            }
            RestartCause cause = new RestartCause();
            Listeners.notify(ComputerListener.class, true, l -> {
                l.onOffline(computer, cause);
            });
        }

        public boolean isReadyToRestart() throws IOException, InterruptedException {
            return true;
        }

        /* loaded from: Jenkins$MasterRestartNotifyier$RestartCause.class */
        private static class RestartCause extends OfflineCause.SimpleOfflineCause {
            protected RestartCause() {
                super(Messages._Jenkins_IsRestarting());
            }
        }
    }

    /* JADX WARN: Type inference failed for: r0v7, types: [jenkins.model.Jenkins$18] */
    @CLIMethod(name = "shutdown")
    @RequirePOST
    public void doExit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        final String exitUser = getAuthentication2().getName();
        final String exitAddr = req != null ? req.getRemoteAddr() : null;
        if (rsp != null) {
            rsp.setStatus(JenkinsLocationConfiguration.ORDINAL);
            rsp.setContentType("text/plain");
            PrintWriter w = rsp.getWriter();
            try {
                w.println("Shutting down");
                if (w != null) {
                    w.close();
                }
            } catch (Throwable th) {
                if (w != null) {
                    try {
                        w.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        new Thread("exit thread") { // from class: jenkins.model.Jenkins.18
            @Override // java.lang.Thread, java.lang.Runnable
            @SuppressFBWarnings(value = {"DM_EXIT"}, justification = "Exit is really intended.")
            public void run() {
                try {
                    ACLContext ctx = ACL.as2(ACL.SYSTEM2);
                    try {
                        Jenkins.this.getLifecycle().onStop(exitUser, exitAddr);
                        Jenkins.this.cleanUp();
                        System.exit(0);
                        if (ctx != null) {
                            ctx.close();
                        }
                    } finally {
                    }
                } catch (Throwable e) {
                    Jenkins.LOGGER.log(Level.WARNING, "Failed to shut down Jenkins", e);
                }
            }
        }.start();
    }

    /* JADX WARN: Type inference failed for: r0v7, types: [jenkins.model.Jenkins$19] */
    @CLIMethod(name = "safe-shutdown")
    @RequirePOST
    public HttpResponse doSafeExit(StaplerRequest2 req) throws IOException {
        checkPermission(ADMINISTER);
        this.quietDownInfo = new QuietDownInfo();
        final String exitUser = getAuthentication2().getName();
        final String exitAddr = req != null ? req.getRemoteAddr() : null;
        new Thread("safe-exit thread") { // from class: jenkins.model.Jenkins.19
            @Override // java.lang.Thread, java.lang.Runnable
            @SuppressFBWarnings(value = {"DM_EXIT"}, justification = "Exit is really intended.")
            public void run() {
                try {
                    ACLContext ctx = ACL.as2(ACL.SYSTEM2);
                    try {
                        Jenkins.this.getLifecycle().onStop(exitUser, exitAddr);
                        Jenkins.this.doQuietDown(true, 0, null);
                        if (Jenkins.this.isQuietingDown()) {
                            Jenkins.this.cleanUp();
                            System.exit(0);
                        }
                        if (ctx != null) {
                            ctx.close();
                        }
                    } finally {
                    }
                } catch (Exception e) {
                    Jenkins.LOGGER.log(Level.WARNING, "Failed to shut down Jenkins", (Throwable) e);
                }
            }
        }.start();
        return HttpResponses.plainText("Shutting down as soon as all jobs are complete");
    }

    @StaplerNotDispatchable
    @Deprecated
    public HttpResponse doSafeExit(StaplerRequest req) throws IOException {
        return doSafeExit(req != null ? StaplerRequest.toStaplerRequest2(req) : null);
    }

    @NonNull
    public static Authentication getAuthentication2() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) {
            a = ANONYMOUS2;
        }
        return a;
    }

    @NonNull
    @Deprecated
    public static org.acegisecurity.Authentication getAuthentication() {
        return org.acegisecurity.Authentication.fromSpring(getAuthentication2());
    }

    public void doScript(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        _doScript(req, rsp, req.getView(this, "_script.jelly"), (VirtualChannel) FilePath.localChannel, getACL());
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            _doScript(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), StaplerRequest.toStaplerRequest2(req).getView(this, "_script.jelly"), (VirtualChannel) FilePath.localChannel, getACL());
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    public void doScriptText(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        _doScript(req, rsp, req.getView(this, "_scriptText.jelly"), (VirtualChannel) FilePath.localChannel, getACL());
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doScriptText(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            _doScript(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), StaplerRequest.toStaplerRequest2(req).getView(this, "_scriptText.jelly"), (VirtualChannel) FilePath.localChannel, getACL());
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    public static void _doScript(StaplerRequest2 req, StaplerResponse2 rsp, RequestDispatcher view, VirtualChannel channel, ACL acl) throws IOException, ServletException {
        acl.checkPermission(ADMINISTER);
        String text = req.getParameter("script");
        if (text != null) {
            if (!"POST".equals(req.getMethod())) {
                throw HttpResponses.error(405, "requires POST");
            }
            if (channel == null) {
                throw HttpResponses.error(404, "Node is offline");
            }
            try {
                req.setAttribute("output", RemotingDiagnostics.executeGroovy(text, channel));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServletException(e);
            }
        }
        view.forward(req, rsp);
    }

    @Deprecated
    public static void _doScript(StaplerRequest req, StaplerResponse rsp, javax.servlet.RequestDispatcher view, VirtualChannel channel, ACL acl) throws IOException, javax.servlet.ServletException {
        try {
            _doScript(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), RequestDispatcherWrapper.toJakartaRequestDispatcher(view), channel, acl);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    @RequirePOST
    public void doEval(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(ADMINISTER);
        req.getWebApp().getDispatchValidator().allowDispatch(req, rsp);
        try {
            MetaClass mc = req.getWebApp().getMetaClass(getClass());
            Script script = ((JellyClassLoaderTearOff) mc.classLoader.loadTearOff(JellyClassLoaderTearOff.class)).createContext().compileScript(new InputSource(req.getReader()));
            new JellyRequestDispatcher(this, script).forward(req, rsp);
        } catch (JellyException e) {
            throw new ServletException(e);
        }
    }

    public void doSignup(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (getSecurityRealm().allowsSignup()) {
            req.getView(getSecurityRealm(), "signup.jelly").forward(req, rsp);
        } else {
            req.getView(SecurityRealm.class, "signup.jelly").forward(req, rsp);
        }
    }

    @SuppressFBWarnings(value = {"INSECURE_COOKIE"}, justification = "TODO needs triage")
    public void doIconSize(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String qs = req.getQueryString();
        if (qs == null) {
            throw new ServletException();
        }
        Cookie cookie = new Cookie("iconSize", Functions.validateIconSize(qs));
        cookie.setMaxAge(9999999);
        cookie.setSecure(req.isSecure());
        cookie.setHttpOnly(true);
        rsp.addCookie(cookie);
        String ref = req.getHeader("Referer");
        if (ref == null) {
            ref = ".";
        }
        rsp.sendRedirect2(ref);
    }

    @RequirePOST
    public void doFingerprintCleanup(StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        FingerprintCleanupThread.invoke();
        rsp.setStatus(JenkinsLocationConfiguration.ORDINAL);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    @RequirePOST
    public void doWorkspaceCleanup(StaplerResponse2 rsp) throws IOException {
        checkPermission(ADMINISTER);
        WorkspaceCleanupThread.invoke();
        rsp.setStatus(JenkinsLocationConfiguration.ORDINAL);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    /* JADX WARN: Multi-variable type inference failed */
    public FormValidation doDefaultJDKCheck(StaplerRequest2 request, @QueryParameter String value) {
        if (!JDK.isDefaultName(value)) {
            return FormValidation.ok();
        }
        if (JDK.isDefaultJDKValid(this)) {
            return FormValidation.ok();
        }
        return FormValidation.errorWithMarkup(Messages.Hudson_NoJavaInPath(request.getContextPath()));
    }

    public FormValidation doCheckViewName(@QueryParameter String value) {
        checkPermission(View.CREATE);
        String name = Util.fixEmpty(value);
        if (name == null) {
            return FormValidation.ok();
        }
        if (getView(name) != null) {
            return FormValidation.error(Messages.Hudson_ViewAlreadyExists(name));
        }
        try {
            checkGoodName(name);
            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    @Deprecated
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);
        String view = Util.fixEmpty(value);
        if (view == null) {
            return FormValidation.ok();
        }
        if (getView(view) == null) {
            return FormValidation.ok();
        }
        return FormValidation.error(Messages.Hudson_ViewAlreadyExists(view));
    }

    public void doResources(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        URL url;
        String path = req.getRestOfPath();
        String path2 = path.substring(path.indexOf(47, 1) + 1);
        int idx = path2.lastIndexOf(46);
        String extension = path2.substring(idx + 1);
        if (ALLOWED_RESOURCE_EXTENSIONS.contains(extension) && (url = this.pluginManager.uberClassLoader.getResource(path2)) != null) {
            long expires = MetaClass.NO_CACHE ? 0L : TimeUnit.DAYS.toMillis(365L);
            rsp.serveFile(req, url, expires);
        } else {
            rsp.sendError(404);
        }
    }

    @Restricted({NoExternalUse.class})
    @RestrictedSince("2.37")
    @Deprecated
    public FormValidation doCheckURIEncoding(StaplerRequest2 request) throws IOException {
        return ((URICheckEncodingMonitor) ExtensionList.lookupSingleton(URICheckEncodingMonitor.class)).doCheckURIEncoding(request);
    }

    @Restricted({NoExternalUse.class})
    @RestrictedSince("2.37")
    @Deprecated
    public static boolean isCheckURIEncodingEnabled() {
        return ((URICheckEncodingMonitor) ExtensionList.lookupSingleton(URICheckEncodingMonitor.class)).isCheckEnabled();
    }

    public Future<DependencyGraph> getFutureDependencyGraph() {
        Future<DependencyGraph> future;
        synchronized (this.dependencyGraphLock) {
            future = (Future) Objects.requireNonNullElseGet(this.scheduledFutureDependencyGraph, () -> {
                return (Future) Objects.requireNonNullElseGet(this.calculatingFutureDependencyGraph, () -> {
                    return CompletableFuture.completedFuture(this.dependencyGraph);
                });
            });
        }
        return future;
    }

    public void rebuildDependencyGraph() {
        DependencyGraph graph = new DependencyGraph();
        graph.build();
        this.dependencyGraph = graph;
    }

    public Future<DependencyGraph> rebuildDependencyGraphAsync() {
        Future<DependencyGraph> future;
        synchronized (this.dependencyGraphLock) {
            future = (Future) Objects.requireNonNullElseGet(this.scheduledFutureDependencyGraph, () -> {
                Future<DependencyGraph> scheduleCalculationOfFutureDependencyGraph = scheduleCalculationOfFutureDependencyGraph(500, TimeUnit.MILLISECONDS);
                this.scheduledFutureDependencyGraph = scheduleCalculationOfFutureDependencyGraph;
                return scheduleCalculationOfFutureDependencyGraph;
            });
        }
        return future;
    }

    private Future<DependencyGraph> scheduleCalculationOfFutureDependencyGraph(int delay, TimeUnit unit) {
        return jenkins.util.Timer.get().schedule(() -> {
            Future<DependencyGraph> temp = null;
            synchronized (this.dependencyGraphLock) {
                if (this.calculatingFutureDependencyGraph != null) {
                    temp = this.calculatingFutureDependencyGraph;
                }
            }
            if (temp != null) {
                temp.get();
            }
            synchronized (this.dependencyGraphLock) {
                this.calculatingFutureDependencyGraph = this.scheduledFutureDependencyGraph;
                this.scheduledFutureDependencyGraph = null;
            }
            rebuildDependencyGraph();
            synchronized (this.dependencyGraphLock) {
                this.calculatingFutureDependencyGraph = null;
            }
            return this.dependencyGraph;
        }, delay, unit);
    }

    public DependencyGraph getDependencyGraph() {
        return this.dependencyGraph;
    }

    public List<ManagementLink> getManagementLinks() {
        return ManagementLink.all();
    }

    @Restricted({NoExternalUse.class})
    public Map<ManagementLink.Category, List<ManagementLink>> getCategorizedManagementLinks() {
        Map<ManagementLink.Category, List<ManagementLink>> byCategory = new TreeMap<>();
        Iterator it = ManagementLink.all().iterator();
        while (it.hasNext()) {
            ManagementLink link = (ManagementLink) it.next();
            if (link.getIconFileName() != null && link.hasRequiredPermission()) {
                byCategory.computeIfAbsent(link.getCategory(), c -> {
                    return new ArrayList();
                }).add(link);
            }
        }
        return byCategory;
    }

    @Restricted({NoExternalUse.class})
    public SetupWizard getSetupWizard() {
        return this.setupWizard;
    }

    public User getMe() {
        User u = User.current();
        if (u == null) {
            throw new AccessDeniedException("/me is not available when not logged in");
        }
        return u;
    }

    @StaplerDispatchable
    public List<Widget> getWidgets() {
        return this.widgets;
    }

    public Object getTarget() {
        try {
            checkPermission(READ);
            return this;
        } catch (AccessDeniedException e) {
            if (!isSubjectToMandatoryReadPermissionCheck(Stapler.getCurrentRequest2().getRestOfPath())) {
                return this;
            }
            throw e;
        }
    }

    public boolean isSubjectToMandatoryReadPermissionCheck(String restOfPath) {
        for (String name : ALWAYS_READABLE_PATHS) {
            if (restOfPath.startsWith("/" + name + "/") || restOfPath.equals("/" + name)) {
                return false;
            }
        }
        for (String name2 : getUnprotectedRootActions()) {
            if (restOfPath.startsWith("/" + name2 + "/") || restOfPath.equals("/" + name2)) {
                return false;
            }
        }
        if ((isAgentJnlpPath(restOfPath, "jenkins") || isAgentJnlpPath(restOfPath, "slave")) && "true".equals(Stapler.getCurrentRequest2().getParameter("encrypt"))) {
            return false;
        }
        return true;
    }

    private boolean isAgentJnlpPath(String restOfPath, String prefix) {
        return restOfPath.matches("(/manage)?/computer/[^/]+/" + prefix + "-agent[.]jnlp");
    }

    public Collection<String> getUnprotectedRootActions() {
        String url;
        Set<String> names = new TreeSet<>();
        names.add("jnlpJars");
        for (Action a : getActions()) {
            if ((a instanceof UnprotectedRootAction) && (url = a.getUrlName()) != null) {
                names.add(url);
            }
        }
        return names;
    }

    /* renamed from: getStaplerFallback, reason: merged with bridge method [inline-methods] */
    public View m106getStaplerFallback() {
        return getPrimaryView();
    }

    boolean isDisplayNameUnique(ItemGroup<?> itemGroup, String displayName, String currentJobName) {
        Collection<TopLevelItem> itemCollection = itemGroup.getItems(t -> {
            return t instanceof TopLevelItem;
        });
        for (TopLevelItem item : itemCollection) {
            if (!item.getName().equals(currentJobName) && displayName.equals(item.getDisplayName())) {
                return false;
            }
        }
        return true;
    }

    boolean isNameUnique(ItemGroup<?> itemGroup, String name, String currentJobName) {
        Item item = itemGroup.getItem(name);
        if (null == item || item.getName().equals(currentJobName)) {
            return true;
        }
        return false;
    }

    @Deprecated
    public FormValidation doCheckDisplayName(@QueryParameter String displayName, @QueryParameter String jobName) {
        String displayName2 = displayName.trim();
        LOGGER.fine(() -> {
            return "Current job name is " + jobName;
        });
        if (!isNameUnique(this, displayName2, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_NameNotUniqueWarning(displayName2));
        }
        if (!isDisplayNameUnique(this, displayName2, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_DisplayNameNotUniqueWarning(displayName2));
        }
        return FormValidation.ok();
    }

    @Restricted({NoExternalUse.class})
    public FormValidation checkDisplayName(String displayName, TopLevelItem item) {
        String displayName2 = displayName.trim();
        String jobName = item.getName();
        LOGGER.fine(() -> {
            return "Current job name is " + jobName;
        });
        if (!isNameUnique(item.getParent(), displayName2, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_NameNotUniqueWarning(displayName2));
        }
        if (!isDisplayNameUnique(item.getParent(), displayName2, jobName)) {
            return FormValidation.warning(Messages.Jenkins_CheckDisplayName_DisplayNameNotUniqueWarning(displayName2));
        }
        return FormValidation.ok();
    }

    /* loaded from: Jenkins$MasterComputer.class */
    public static class MasterComputer extends Computer {

        @Deprecated
        public static final LocalChannel localChannel = FilePath.localChannel;

        protected MasterComputer() {
            super(Jenkins.get());
        }

        @NonNull
        public String getName() {
            return "";
        }

        public boolean isConnecting() {
            return false;
        }

        @NonNull
        public String getDisplayName() {
            return Messages.Hudson_Computer_DisplayName();
        }

        public String getCaption() {
            return Messages.Hudson_Computer_Caption();
        }

        @NonNull
        public String getUrl() {
            return "computer/(built-in)/";
        }

        public RetentionStrategy getRetentionStrategy() {
            return RetentionStrategy.NOOP;
        }

        protected boolean isAlive() {
            return true;
        }

        public Boolean isUnix() {
            return Boolean.valueOf(!Functions.isWindows());
        }

        public HttpResponse doDoDelete() throws IOException {
            throw HttpResponses.status(400);
        }

        /* JADX WARN: Multi-variable type inference failed */
        @POST
        public void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
            checkPermission(Jenkins.ADMINISTER);
            Jenkins jenkins2 = Jenkins.get();
            BulkChange bc = new BulkChange(jenkins2);
            try {
                JSONObject json = req.getSubmittedForm();
                try {
                    String num = json.getString("numExecutors");
                    if (!num.matches("\\d+")) {
                        throw new Descriptor.FormException(Messages.Hudson_Computer_IncorrectNumberOfExecutors(), "numExecutors");
                    }
                    jenkins2.setNumExecutors(json.getInt("numExecutors"));
                    if (req.hasParameter("builtin.mode")) {
                        jenkins2.setMode(Node.Mode.valueOf(req.getParameter("builtin.mode")));
                    } else {
                        jenkins2.setMode(Node.Mode.NORMAL);
                    }
                    jenkins2.setLabelString(json.optString("labelString", ""));
                    jenkins2.getNodeProperties().rebuild(req, json.optJSONObject("nodeProperties"), NodeProperty.all());
                    bc.commit();
                    bc.close();
                    jenkins2.updateComputers(jenkins2);
                    Computer computer = jenkins2.toComputer();
                    if (computer == null) {
                        throw new IllegalStateException("Cannot find the computer object for the controller node");
                    }
                    FormApply.success(req.getContextPath() + "/" + computer.getUrl()).generateResponse(req, rsp, (Object) null);
                } catch (IOException e) {
                    throw new Descriptor.FormException(e, "numExecutors");
                }
            } catch (Throwable th) {
                try {
                    bc.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }

        @WebMethod(name = {"config.xml"})
        public void doConfigDotXml(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            throw HttpResponses.status(400);
        }

        public boolean hasPermission(Permission permission) {
            if (permission == Computer.DELETE) {
                return false;
            }
            return super.hasPermission(permission == Computer.CONFIGURE ? Jenkins.ADMINISTER : permission);
        }

        public VirtualChannel getChannel() {
            return FilePath.localChannel;
        }

        public Charset getDefaultCharset() {
            return Charset.defaultCharset();
        }

        public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
            return Jenkins.logRecords;
        }

        @RequirePOST
        public void doLaunchSlaveAgent(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            rsp.sendError(404);
        }

        protected Future<?> _connect(boolean forceReconnect) {
            return Futures.precomputed((Object) null);
        }
    }

    @CheckForNull
    public static <T> T lookup(Class<T> cls) {
        Jenkins instanceOrNull = getInstanceOrNull();
        if (instanceOrNull != null) {
            return (T) instanceOrNull.lookup.get(cls);
        }
        return null;
    }

    private static void computeVersion(jakarta.servlet.ServletContext context) {
        Properties props = new Properties();
        try {
            InputStream is = Jenkins.class.getResourceAsStream("jenkins-version.properties");
            if (is != null) {
                try {
                    props.load(is);
                } finally {
                }
            }
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> {
                return "Failed to load jenkins-version.properties";
            });
        }
        String ver = props.getProperty("version");
        if (ver == null) {
            ver = UNCOMPUTED_VERSION;
        }
        if (Main.isDevelopmentMode && "${project.version}".equals(ver)) {
            try {
                File dir = new File(".").getAbsoluteFile();
                while (true) {
                    if (dir == null) {
                        break;
                    }
                    File pom = new File(dir, "pom.xml");
                    if (pom.exists() && "pom".equals(XMLUtils.getValue("/project/artifactId", pom))) {
                        File pom2 = pom.getCanonicalFile();
                        LOGGER.info("Reading version from: " + pom2.getAbsolutePath());
                        ver = XMLUtils.getValue("/project/version", pom2);
                        break;
                    }
                    dir = dir.getParentFile();
                }
                LOGGER.info("Jenkins is in dev mode, using version: " + ver);
            } catch (Exception e2) {
                LOGGER.log(Level.WARNING, e2, () -> {
                    return "Unable to read Jenkins version: " + e2.getMessage();
                });
            }
        }
        VERSION = ver;
        context.setAttribute("version", ver);
        CHANGELOG_URL = props.getProperty("changelog.url");
        VERSION_HASH = Util.getDigestOf(ver).substring(0, 8);
        SESSION_HASH = Util.getDigestOf(ver + System.currentTimeMillis()).substring(0, 8);
        if (ver.equals(UNCOMPUTED_VERSION) || SystemProperties.getBoolean("hudson.script.noCache")) {
            RESOURCE_PATH = "";
        } else {
            RESOURCE_PATH = "/static/" + SESSION_HASH;
        }
        VIEW_RESOURCE_PATH = "/resources/" + SESSION_HASH;
    }

    @CheckForNull
    public static VersionNumber getVersion() {
        return toVersion(VERSION);
    }

    @CheckForNull
    @Restricted({NoExternalUse.class})
    public static VersionNumber getStoredVersion() {
        return toVersion(get().version);
    }

    @CheckForNull
    private static VersionNumber toVersion(@CheckForNull String versionString) {
        if (versionString == null) {
            return null;
        }
        try {
            return new VersionNumber(versionString);
        } catch (NumberFormatException e) {
            try {
                int idx = versionString.indexOf(32);
                if (idx > 0) {
                    return new VersionNumber(versionString.substring(0, idx));
                }
                return null;
            } catch (NumberFormatException e2) {
                return null;
            }
        } catch (IllegalArgumentException e3) {
            return null;
        }
    }

    @Restricted({NoExternalUse.class})
    public boolean shouldShowStackTrace() {
        return Boolean.getBoolean(Jenkins.class.getName() + ".SHOW_STACK_TRACE");
    }

    /* loaded from: Jenkins$JenkinsJVMAccess.class */
    private static final class JenkinsJVMAccess extends JenkinsJVM {
        private JenkinsJVMAccess() {
        }

        private static void _setJenkinsJVM(boolean jenkinsJVM) {
            JenkinsJVM.setJenkinsJVM(jenkinsJVM);
        }
    }

    /* loaded from: Jenkins$QuietDownInfo.class */
    private static final class QuietDownInfo {

        @CheckForNull
        final String message;
        private boolean safeRestart;

        QuietDownInfo() {
            this(null, false);
        }

        QuietDownInfo(final String message) {
            this(message, false);
        }

        QuietDownInfo(final String message, final boolean safeRestart) {
            this.message = message;
            this.safeRestart = safeRestart;
        }

        boolean isSafeRestart() {
            return this.safeRestart;
        }

        void setSafeRestart(boolean safeRestart) {
            this.safeRestart = safeRestart;
        }
    }
}
