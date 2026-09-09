package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.XStreamDeserializable;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/* loaded from: JDK.class */
public final class JDK extends ToolInstallation implements NodeSpecific<JDK>, EnvironmentSpecific<JDK> {
    public static final String DEFAULT_NAME = "(System)";
    private static final long serialVersionUID = -3318291200160313357L;

    @XStreamDeserializable
    @Deprecated
    private transient String javaHome;
    private static final Logger LOGGER = Logger.getLogger(JDK.class.getName());

    @Restricted({NoExternalUse.class})
    public static boolean isDefaultName(String name) {
        return "(Default)".equals(name) || DEFAULT_NAME.equals(name) || name == null;
    }

    public JDK(String name, String javaHome) {
        super(name, javaHome, Collections.emptyList());
    }

    @DataBoundConstructor
    public JDK(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    @Deprecated
    public String getJavaHome() {
        return getHome();
    }

    public File getBinDir() {
        return new File(getHome(), "bin");
    }

    private File getExecutable() {
        String execName = File.separatorChar == '\\' ? "java.exe" : "java";
        return new File(getHome(), "bin/" + execName);
    }

    public boolean getExists() {
        return getExecutable().exists();
    }

    @Deprecated
    public void buildEnvVars(Map<String, String> env) {
        String home = getHome();
        if (home == null) {
            return;
        }
        env.put("PATH+JDK", home + "/bin");
        env.put("JAVA_HOME", home);
    }

    public void buildEnvVars(EnvVars env) {
        buildEnvVars((Map<String, String>) env);
    }

    /* renamed from: forNode, reason: merged with bridge method [inline-methods] */
    public JDK m24forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new JDK(getName(), translateFor(node, log));
    }

    /* renamed from: forEnvironment, reason: merged with bridge method [inline-methods] */
    public JDK m25forEnvironment(EnvVars environment) {
        return new JDK(getName(), environment.expand(getHome()));
    }

    public static boolean isDefaultJDKValid(Node n) {
        try {
            StreamTaskListener streamTaskListener = new StreamTaskListener(OutputStream.nullOutputStream());
            Launcher launcher = n.createLauncher(streamTaskListener);
            return launcher.launch().cmds(new String[]{"java", "-fullversion"}).stdout(streamTaskListener).join() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Extension
    @Symbol({"jdk"})
    /* loaded from: JDK$DescriptorImpl.class */
    public static class DescriptorImpl extends ToolDescriptor<JDK> {
        @NonNull
        public String getDisplayName() {
            return Messages.JDK_DisplayName();
        }

        /* renamed from: getInstallations, reason: merged with bridge method [inline-methods] */
        public JDK[] m26getInstallations() {
            return (JDK[]) Jenkins.get().getJDKs().toArray(new JDK[0]);
        }

        public void setInstallations(JDK... jdks) {
            Jenkins.get().setJDKs(Arrays.asList(jdks));
        }

        public List<? extends ToolInstaller> getDefaultInstallers() {
            try {
                Constructor<? extends ToolInstaller> constructor = Jenkins.get().getPluginManager().uberClassLoader.loadClass("hudson.tools.JDKInstaller").asSubclass(ToolInstaller.class).getConstructor(String.class, Boolean.TYPE);
                return List.of((ToolInstaller) constructor.newInstance(null, false));
            } catch (ClassNotFoundException e) {
                return Collections.emptyList();
            } catch (Exception e2) {
                JDK.LOGGER.log(Level.WARNING, "Unable to get default installer", (Throwable) e2);
                return Collections.emptyList();
            }
        }

        protected FormValidation checkHomeDirectory(File value) {
            File toolsJar = new File(value, "lib/tools.jar");
            File mac = new File(value, "lib/dt.jar");
            File javac = new File(value, "bin/javac");
            File javacExe = new File(value, "bin/javac.exe");
            if (!toolsJar.exists() && !mac.exists() && !javac.exists() && !javacExe.exists()) {
                return FormValidation.error(Messages.Hudson_NotJDKDir(value));
            }
            return FormValidation.ok();
        }
    }

    /* loaded from: JDK$ConverterImpl.class */
    public static class ConverterImpl extends ToolInstallation.ToolConverter {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        protected String oldHomeField(ToolInstallation obj) {
            return ((JDK) obj).javaHome;
        }
    }
}
