package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.userproperty.UserPropertyCategory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.XStreamNotDeserializable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
/* loaded from: UserProperty.class */
public abstract class UserProperty implements ReconfigurableDescribable<UserProperty>, ExtensionPoint {

    @XStreamNotDeserializable
    protected transient User user;

    protected void setUser(User u) {
        this.user = u;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public UserPropertyDescriptor m61getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<UserProperty, UserPropertyDescriptor> all() {
        return Jenkins.get().getDescriptorList(UserProperty.class);
    }

    public static List<UserPropertyDescriptor> allByCategoryClass(@NonNull Class<? extends UserPropertyCategory> categoryClass) {
        DescriptorExtensionList<UserProperty, UserPropertyDescriptor> all = all();
        List<UserPropertyDescriptor> onlyForTheCategory = new ArrayList<>(all.size());
        Iterator it = all.iterator();
        while (it.hasNext()) {
            UserPropertyDescriptor descriptor = (UserPropertyDescriptor) it.next();
            if (descriptor.getUserPropertyCategory().getClass().equals(categoryClass)) {
                onlyForTheCategory.add(descriptor);
            }
        }
        return onlyForTheCategory;
    }

    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public UserProperty m60reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(UserProperty.class, getClass(), "reconfigure", new Class[]{StaplerRequest.class, JSONObject.class})) {
            return m59reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        }
        return reconfigureImpl(req, form);
    }

    @Deprecated
    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public UserProperty m59reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private UserProperty reconfigureImpl(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (form == null) {
            return null;
        }
        return m61getDescriptor().newInstance(req, form);
    }
}
