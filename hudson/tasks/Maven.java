package hudson.tasks;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.PersistentDescriptor;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tasks._maven.MavenConsoleAnnotator;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import hudson.util.VariableResolver;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/* loaded from: Maven.class */
public class Maven extends Builder {
    public final String targets;
    public final String mavenName;
    public final String jvmOptions;
    public final String pom;
    public final String properties;

    @SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE"}, justification = "Preserve API compatibility")
    public boolean usePrivateRepository;
    private SettingsProvider settings;
    private GlobalSettingsProvider globalSettings;

    @NonNull
    private Boolean injectBuildVariables;
    private static final String MAVEN_1_INSTALLATION_COMMON_FILE = "bin/maven";
    private static final String MAVEN_2_INSTALLATION_COMMON_FILE = "bin/mvn";
    private static final Pattern S_PATTERN = Pattern.compile("(^| )-s ");
    private static final Pattern GS_PATTERN = Pattern.compile("(^| )-gs ");

    @Restricted({NoExternalUse.class})
    @Deprecated
    public static DescriptorImpl DESCRIPTOR;

    public Maven(String targets, String name) {
        this(targets, name, null, null, null, false, null, null);
    }

    public Maven(String targets, String name, String pom, String properties, String jvmOptions) {
        this(targets, name, pom, properties, jvmOptions, false, null, null);
    }

    public Maven(String targets, String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository) {
        this(targets, name, pom, properties, jvmOptions, usePrivateRepository, null, null);
    }

    public Maven(String targets, String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository, SettingsProvider settings, GlobalSettingsProvider globalSettings) {
        this(targets, name, pom, properties, jvmOptions, usePrivateRepository, settings, globalSettings, false);
    }

    @DataBoundConstructor
    public Maven(String targets, String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository, SettingsProvider settings, GlobalSettingsProvider globalSettings, boolean injectBuildVariables) {
        this.targets = targets;
        this.mavenName = name;
        this.pom = Util.fixEmptyAndTrim(pom);
        this.properties = Util.fixEmptyAndTrim(properties);
        this.jvmOptions = Util.fixEmptyAndTrim(jvmOptions);
        this.usePrivateRepository = usePrivateRepository;
        this.settings = settings != null ? settings : GlobalMavenConfig.get().getSettingsProvider();
        this.globalSettings = globalSettings != null ? globalSettings : GlobalMavenConfig.get().getGlobalSettingsProvider();
        this.injectBuildVariables = Boolean.valueOf(injectBuildVariables);
    }

    public String getTargets() {
        return this.targets;
    }

    public SettingsProvider getSettings() {
        return this.settings != null ? this.settings : GlobalMavenConfig.get().getSettingsProvider();
    }

    protected void setSettings(SettingsProvider settings) {
        this.settings = settings;
    }

    public GlobalSettingsProvider getGlobalSettings() {
        return this.globalSettings != null ? this.globalSettings : GlobalMavenConfig.get().getGlobalSettingsProvider();
    }

    protected void setGlobalSettings(GlobalSettingsProvider globalSettings) {
        this.globalSettings = globalSettings;
    }

    public void setUsePrivateRepository(boolean usePrivateRepository) {
        this.usePrivateRepository = usePrivateRepository;
    }

    public boolean usesPrivateRepository() {
        return this.usePrivateRepository;
    }

    @Restricted({NoExternalUse.class})
    public boolean isInjectBuildVariables() {
        return this.injectBuildVariables.booleanValue();
    }

    public MavenInstallation getMaven() {
        for (MavenInstallation i : m87getDescriptor().getInstallations()) {
            if (this.mavenName != null && this.mavenName.equals(i.getName())) {
                return i;
            }
        }
        return null;
    }

    @SuppressFBWarnings(value = {"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"}, justification = "injectBuildVariables in readResolve is needed for data migration.")
    private Object readResolve() {
        if (this.injectBuildVariables == null) {
            this.injectBuildVariables = true;
        }
        return this;
    }

    /* loaded from: Maven$DecideDefaultMavenCommand.class */
    private static final class DecideDefaultMavenCommand extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = -2327576423452215146L;
        private final String arguments;

        DecideDefaultMavenCommand(String arguments) {
            this.arguments = arguments;
        }

        /* renamed from: invoke, reason: merged with bridge method [inline-methods] */
        public String m88invoke(File ws, VirtualChannel channel) throws IOException {
            String str;
            String seed = null;
            StringTokenizer tokens = new StringTokenizer(this.arguments);
            while (true) {
                if (!tokens.hasMoreTokens()) {
                    break;
                }
                String t = tokens.nextToken();
                if (t.equals("-f") && tokens.hasMoreTokens()) {
                    File file = new File(ws, tokens.nextToken());
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            str = "maven";
                        } else {
                            str = "mvn";
                        }
                        seed = str;
                    }
                }
            }
            if (seed == null) {
                seed = new File(ws, "project.xml").exists() ? "maven" : "mvn";
            }
            return seed;
        }
    }

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        String settingsPath;
        String settingsPath2;
        VariableResolver<String> vr = build.getBuildVariableResolver();
        EnvVars env = build.getEnvironment(listener);
        String targets = env.expand(Util.replaceMacro(this.targets, vr));
        String pom = env.expand(this.pom);
        int startIndex = 0;
        do {
            int endIndex = targets.indexOf(124, startIndex);
            if (-1 == endIndex) {
                endIndex = targets.length();
            }
            String normalizedTarget = targets.substring(startIndex, endIndex).replaceAll("[\t\r\n]+", " ");
            ArgumentListBuilder args = new ArgumentListBuilder();
            MavenInstallation mi = getMaven();
            if (mi == null) {
                String execName = (String) build.getWorkspace().act(new DecideDefaultMavenCommand(normalizedTarget));
                args.add(execName);
            } else {
                mi = mi.m91forNode(Computer.currentComputer().getNode(), (TaskListener) listener).m90forEnvironment(env);
                String exec = mi.getExecutable(launcher);
                if (exec == null) {
                    listener.fatalError(Messages.Maven_NoExecutable(mi.getHome()));
                    return false;
                }
                args.add(exec);
            }
            if (pom != null) {
                args.add(new String[]{"-f", pom});
            }
            if (!S_PATTERN.matcher(targets).find() && (settingsPath2 = SettingsProvider.getSettingsRemotePath(getSettings(), build, listener)) != null && !settingsPath2.isBlank()) {
                args.add(new String[]{"-s", settingsPath2});
            }
            if (!GS_PATTERN.matcher(targets).find() && (settingsPath = GlobalSettingsProvider.getSettingsRemotePath(getGlobalSettings(), build, listener)) != null && !settingsPath.isBlank()) {
                args.add(new String[]{"-gs", settingsPath});
            }
            Set<String> sensitiveVars = build.getSensitiveBuildVariables();
            if (isInjectBuildVariables()) {
                args.addKeyValuePairs("-D", build.getBuildVariables(), sensitiveVars);
            }
            args.addKeyValuePairsFromPropertyString("-D", this.properties, new VariableResolver.Union(new VariableResolver[]{new VariableResolver.ByMap(env), vr}), sensitiveVars);
            if (usesPrivateRepository()) {
                args.add("-Dmaven.repo.local=" + String.valueOf(build.getWorkspace().child(".repository")));
            }
            args.addTokenized(normalizedTarget);
            wrapUpArguments(args, normalizedTarget, build, launcher, listener);
            buildEnvVars(env, mi);
            if (!launcher.isUnix()) {
                args = args.toWindowsCommand();
            }
            try {
                MavenConsoleAnnotator mca = new MavenConsoleAnnotator(listener.getLogger(), build.getCharset());
                int r = launcher.launch().cmds(args).envs(env).stdout(mca).pwd(build.getModuleRoot()).join();
                if (0 != r) {
                    return false;
                }
                startIndex = endIndex + 1;
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                Functions.printStackTrace(e, listener.fatalError(Messages.Maven_ExecFailed()));
                return false;
            }
        } while (startIndex < targets.length());
        return true;
    }

    protected void wrapUpArguments(ArgumentListBuilder args, String normalizedTarget, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    }

    protected void buildEnvVars(EnvVars env, MavenInstallation mi) throws IOException, InterruptedException {
        if (mi != null) {
            mi.buildEnvVars(env);
        }
        env.put("MAVEN_TERMINATE_CMD", "on");
        String jvmOptions = env.expand(this.jvmOptions);
        if (jvmOptions != null) {
            env.put("MAVEN_OPTS", jvmOptions.replaceAll("[\t\r\n]+", " "));
        }
    }

    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public DescriptorImpl m87getDescriptor() {
        return super.getDescriptor();
    }

    @Extension
    @Symbol({"maven"})
    /* loaded from: Maven$DescriptorImpl.class */
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> implements PersistentDescriptor {
        private volatile MavenInstallation[] installations = new MavenInstallation[0];

        @SuppressFBWarnings(value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"}, justification = "for backward compatibility")
        public DescriptorImpl() {
            Maven.DESCRIPTOR = this;
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getHelpFile(String fieldName) {
            if (fieldName != null && fieldName.equals("globalSettings")) {
                fieldName = "settings";
            }
            return super.getHelpFile(fieldName);
        }

        @NonNull
        public String getDisplayName() {
            return Messages.Maven_DisplayName();
        }

        public GlobalSettingsProvider getDefaultGlobalSettingsProvider() {
            return GlobalMavenConfig.get().getGlobalSettingsProvider();
        }

        public SettingsProvider getDefaultSettingsProvider() {
            return GlobalMavenConfig.get().getSettingsProvider();
        }

        public MavenInstallation[] getInstallations() {
            return this.installations;
        }

        public void setInstallations(MavenInstallation... installations) {
            List<MavenInstallation> tmpList = new ArrayList<>();
            if (installations != null) {
                Collections.addAll(tmpList, installations);
                for (MavenInstallation installation : installations) {
                    if (Util.fixEmptyAndTrim(installation.getName()) == null) {
                        tmpList.remove(installation);
                    }
                }
            }
            this.installations = (MavenInstallation[]) tmpList.toArray(new MavenInstallation[0]);
            save();
        }

        /* renamed from: newInstance, reason: merged with bridge method [inline-methods] */
        public Builder m89newInstance(StaplerRequest2 req, JSONObject formData) throws Descriptor.FormException {
            if (req == null) {
                throw new Descriptor.FormException("Maven Build Step new instance method is called for null Stapler request. Such call is prohibited.", "req");
            }
            return (Builder) req.bindJSON(Maven.class, formData);
        }
    }

    /* loaded from: Maven$MavenInstallation.class */
    public static final class MavenInstallation extends ToolInstallation implements EnvironmentSpecific<MavenInstallation>, NodeSpecific<MavenInstallation> {
        public static final int MAVEN_20 = 0;
        public static final int MAVEN_21 = 1;
        public static final int MAVEN_30 = 2;

        @Deprecated
        private transient String mavenHome;
        private static final long serialVersionUID = 1;

        @Deprecated
        public MavenInstallation(String name, String home) {
            super(name, home);
        }

        @DataBoundConstructor
        public MavenInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
        }

        @Deprecated
        public String getMavenHome() {
            return getHome();
        }

        public File getHomeDir() {
            return new File(getHome());
        }

        public void buildEnvVars(EnvVars env) {
            String home = getHome();
            if (home == null) {
                return;
            }
            env.put("M2_HOME", home);
            env.put("MAVEN_HOME", home);
            env.put("PATH+MAVEN", home + "/bin");
        }

        public boolean meetsMavenReqVersion(Launcher launcher, int mavenReqVersion) throws IOException, InterruptedException {
            String mavenVersion = (String) launcher.getChannel().call(new GetMavenVersion(getHome()));
            if (!mavenVersion.isEmpty()) {
                if (mavenReqVersion == 0) {
                    if (mavenVersion.startsWith("2.")) {
                        return true;
                    }
                    return false;
                }
                if (mavenReqVersion == 1) {
                    if (mavenVersion.startsWith("2.") && !mavenVersion.startsWith("2.0")) {
                        return true;
                    }
                    return false;
                }
                if (mavenReqVersion == 2 && mavenVersion.startsWith("3.")) {
                    return true;
                }
                return false;
            }
            return false;
        }

        /* loaded from: Maven$MavenInstallation$GetMavenVersion.class */
        private static class GetMavenVersion extends MasterToSlaveCallable<String, IOException> {
            private final String home;

            GetMavenVersion(String home) {
                this.home = home;
            }

            /* renamed from: call, reason: merged with bridge method [inline-methods] */
            public String m94call() throws IOException {
                File[] jars = new File(this.home, "lib").listFiles();
                if (jars != null) {
                    for (File jar : jars) {
                        if (jar.getName().startsWith("maven-")) {
                            JarFile jf = new JarFile(jar);
                            try {
                                Manifest manifest = jf.getManifest();
                                String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                                if (version != null) {
                                    jf.close();
                                    return version;
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
                    return "";
                }
                return "";
            }
        }

        public boolean isMaven2_1(Launcher launcher) throws IOException, InterruptedException {
            return meetsMavenReqVersion(launcher, 1);
        }

        public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
            return (String) launcher.getChannel().call(new GetExecutable(getHome()));
        }

        /* loaded from: Maven$MavenInstallation$GetExecutable.class */
        private static class GetExecutable extends MasterToSlaveCallable<String, IOException> {
            private final String rawHome;

            GetExecutable(String rawHome) {
                this.rawHome = rawHome;
            }

            /* renamed from: call, reason: merged with bridge method [inline-methods] */
            public String m93call() throws IOException {
                File exe = MavenInstallation.getExeFile("mvn", this.rawHome);
                if (exe.exists()) {
                    return exe.getPath();
                }
                File exe2 = MavenInstallation.getExeFile("maven", this.rawHome);
                if (exe2.exists()) {
                    return exe2.getPath();
                }
                return null;
            }
        }

        private static File getExeFile(String execName, String home) {
            String m2Home = Util.replaceMacro(home, EnvVars.masterEnvVars);
            if (Functions.isWindows()) {
                File exeFile = new File(m2Home, "bin/" + execName + ".bat");
                if (!exeFile.exists()) {
                    return new File(m2Home, "bin/" + execName + ".cmd");
                }
                return exeFile;
            }
            return new File(m2Home, "bin/" + execName);
        }

        public boolean getExists() {
            try {
                return getExecutable(new Launcher.LocalLauncher(new StreamTaskListener(OutputStream.nullOutputStream()))) != null;
            } catch (IOException | InterruptedException e) {
                return false;
            }
        }

        /* renamed from: forEnvironment, reason: merged with bridge method [inline-methods] */
        public MavenInstallation m90forEnvironment(EnvVars environment) {
            return new MavenInstallation(getName(), environment.expand(getHome()), getProperties().toList());
        }

        /* renamed from: forNode, reason: merged with bridge method [inline-methods] */
        public MavenInstallation m91forNode(Node node, TaskListener log) throws IOException, InterruptedException {
            return new MavenInstallation(getName(), translateFor(node, log), getProperties().toList());
        }

        @Extension
        @Symbol({"maven"})
        /* loaded from: Maven$MavenInstallation$DescriptorImpl.class */
        public static class DescriptorImpl extends ToolDescriptor<MavenInstallation> {
            @NonNull
            public String getDisplayName() {
                return "Maven";
            }

            public List<? extends ToolInstaller> getDefaultInstallers() {
                return List.of(new MavenInstaller(null));
            }

            /* renamed from: getInstallations, reason: merged with bridge method [inline-methods] */
            public MavenInstallation[] m92getInstallations() {
                return Jenkins.get().getDescriptorByType(DescriptorImpl.class).getInstallations();
            }

            public void setInstallations(MavenInstallation... installations) {
                Jenkins.get().getDescriptorByType(DescriptorImpl.class).setInstallations(installations);
            }

            protected FormValidation checkHomeDirectory(File value) {
                File maven1File = new File(value, Maven.MAVEN_1_INSTALLATION_COMMON_FILE);
                File maven2File = new File(value, Maven.MAVEN_2_INSTALLATION_COMMON_FILE);
                if (!maven1File.exists() && !maven2File.exists()) {
                    return FormValidation.error(Messages.Maven_NotMavenDirectory(value));
                }
                return FormValidation.ok();
            }
        }

        /* loaded from: Maven$MavenInstallation$ConverterImpl.class */
        public static class ConverterImpl extends ToolInstallation.ToolConverter {
            public ConverterImpl(XStream2 xstream) {
                super(xstream);
            }

            protected String oldHomeField(ToolInstallation obj) {
                return ((MavenInstallation) obj).mavenHome;
            }
        }

        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MavenInstallation that = (MavenInstallation) o;
            if (getHome() != null) {
                if (!getHome().equals(that.getHome())) {
                    return false;
                }
            } else if (that.getHome() != null) {
                return false;
            }
            return getName() != null ? getName().equals(that.getName()) : that.getName() == null;
        }

        public int hashCode() {
            int result = getHome() != null ? getHome().hashCode() : 0;
            return (31 * result) + (getName() != null ? getName().hashCode() : 0);
        }
    }

    /* loaded from: Maven$MavenInstaller.class */
    public static class MavenInstaller extends DownloadFromUrlInstaller {
        @DataBoundConstructor
        public MavenInstaller(String id) {
            super(id);
        }

        @Extension
        @Symbol({"maven"})
        /* loaded from: Maven$MavenInstaller$DescriptorImpl.class */
        public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<MavenInstaller> {
            @NonNull
            public String getDisplayName() {
                return Messages.InstallFromApache();
            }

            public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
                return toolType == MavenInstallation.class;
            }
        }
    }
}
