package hudson.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import jenkins.security.XStreamNotDeserializable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/* loaded from: ViewProperty.class */
public class ViewProperty implements ReconfigurableDescribable<ViewProperty>, ExtensionPoint {

    @XStreamNotDeserializable
    protected transient View view;

    final void setView(View view) {
        this.view = view;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* renamed from: getDescriptor, reason: merged with bridge method [inline-methods] */
    public ViewPropertyDescriptor m64getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<ViewProperty, ViewPropertyDescriptor> all() {
        return Jenkins.get().getDescriptorList(ViewProperty.class);
    }

    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public ViewProperty m63reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(ViewProperty.class, getClass(), "reconfigure", new Class[]{StaplerRequest.class, JSONObject.class})) {
            return m62reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        }
        return reconfigureImpl(req, form);
    }

    @Deprecated
    /* renamed from: reconfigure, reason: merged with bridge method [inline-methods] */
    public ViewProperty m62reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private ViewProperty reconfigureImpl(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (form == null) {
            return null;
        }
        return m64getDescriptor().newInstance(req, form);
    }
}
