package jenkins.model;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import jenkins.util.VirtualFile;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/* loaded from: StandardArtifactManager.class */
public class StandardArtifactManager extends ArtifactManager {
    private static final Logger LOG = Logger.getLogger(StandardArtifactManager.class.getName());

    @VisibleForTesting
    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static FilePath.TarCompression TAR_COMPRESSION;
    protected transient Run<?, ?> build;

    static {
        FilePath.TarCompression tarCompression;
        if (SystemProperties.getBoolean(StandardArtifactManager.class.getName() + ".disableTrafficCompression")) {
            tarCompression = FilePath.TarCompression.NONE;
        } else {
            tarCompression = FilePath.TarCompression.GZIP;
        }
        TAR_COMPRESSION = tarCompression;
    }

    public StandardArtifactManager(Run<?, ?> build) {
        onLoad(build);
    }

    public final void onLoad(Run<?, ?> build) {
        this.build = build;
    }

    public void archive(FilePath workspace, Launcher launcher, BuildListener listener, final Map<String, String> artifacts) throws IOException, InterruptedException {
        File dir = getArtifactsDir();
        String description = "transfer of " + artifacts.size() + " files";
        workspace.copyRecursiveTo(new FilePath.ExplicitlySpecifiedDirScanner(artifacts), new FilePath(dir), description, TAR_COMPRESSION);
    }

    public final boolean delete() throws IOException, InterruptedException {
        File ad = getArtifactsDir();
        if (!ad.exists()) {
            LOG.log(Level.FINE, "no such directory {0} to delete for {1}", new Object[]{ad, this.build});
            return false;
        }
        LOG.log(Level.FINE, "deleting {0} for {1}", new Object[]{ad, this.build});
        Util.deleteRecursive(ad);
        return true;
    }

    public VirtualFile root() {
        return VirtualFile.forFile(getArtifactsDir());
    }

    private File getArtifactsDir() {
        return this.build.getArtifactsDir();
    }
}
