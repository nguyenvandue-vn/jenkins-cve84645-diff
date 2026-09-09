package jenkins.appearance;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.console.ConsoleUrlProviderGlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

@Extension
/* loaded from: AppearanceGlobalConfiguration.class */
public class AppearanceGlobalConfiguration extends ManagementLink {
    private static final Logger LOGGER = Logger.getLogger(AppearanceGlobalConfiguration.class.getName());

    @Restricted({NoExternalUse.class})
    public static final Predicate<Descriptor> FILTER = input -> {
        if (input.getCategory() instanceof AppearanceCategory) {
            if (input instanceof ConsoleUrlProviderGlobalConfiguration) {
                return ((ConsoleUrlProviderGlobalConfiguration) input).isEnabled();
            }
            return true;
        }
        return false;
    };

    @NonNull
    public Permission getRequiredPermission() {
        return Jenkins.READ;
    }

    public String getIconFileName() {
        return "symbol-brush-outline";
    }

    @Restricted({NoExternalUse.class})
    public boolean hasPlugins() {
        return !Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER).isEmpty();
    }

    @Restricted({NoExternalUse.class})
    public Collection<Descriptor> getConfigurableDescriptors() {
        return Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER.and(d -> {
            return Jenkins.get().hasPermission(d.getRequiredGlobalConfigPagePermission());
        }));
    }

    @Restricted({NoExternalUse.class})
    public Collection<Descriptor> getReadableDescriptors() {
        return Functions.getSortedDescriptorsForGlobalConfigByDescriptor(FILTER.and(d -> {
            return Jenkins.get().hasPermission(d.getRequiredGlobalConfigPagePermission()) || Jenkins.get().hasPermission(Jenkins.SYSTEM_READ);
        }));
    }

    public String getDisplayName() {
        return Messages.AppearanceGlobalConfiguration_DisplayName();
    }

    public String getDescription() {
        return Messages.AppearanceGlobalConfiguration_Description();
    }

    public String getUrlName() {
        return "appearance";
    }

    @NonNull
    public ManagementLink.Category getCategory() {
        return ManagementLink.Category.CONFIGURATION;
    }

    @POST
    public synchronized void doConfigure(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        boolean result = configure(req, req.getSubmittedForm());
        LOGGER.log(Level.FINE, "appearance saved: " + result);
        FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, (Object) null);
    }

    private boolean configure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException, IOException {
        Jenkins j = Jenkins.get();
        j.checkPermission(Jenkins.MANAGE);
        boolean result = true;
        for (Descriptor d : getConfigurableDescriptors()) {
            result &= configureDescriptor(req, json, d);
        }
        j.save();
        return result;
    }

    private boolean configureDescriptor(StaplerRequest2 req, JSONObject json, Descriptor<?> d) throws Descriptor.FormException {
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject();
        json.putAll(js);
        return d.configure(req, js);
    }
}
