package hudson.model;

import com.infradna.tool.bridge_method_injector.BridgeMethodsAdded;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Functions;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIResolver;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import hudson.widgets.Widget;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.model.queue.ItemDeletion;
import jenkins.security.ExtendedReadRedaction;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import jenkins.util.xml.XMLUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpDeletable;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.access.AccessDeniedException;
import org.xml.sax.SAXException;

@ExportedBean
@BridgeMethodsAdded
/* loaded from: AbstractItem.class */
public abstract class AbstractItem extends Actionable implements Loadable, Item, HttpDeletable, AccessControlled, DescriptorByNameOwner, StaplerProxy {
    private static final Logger LOGGER;
    protected transient String name;
    protected volatile String description;
    private transient ItemGroup parent;
    protected String displayName;

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean SKIP_PERMISSION_CHECK;
    public static final AlternativeUiTextProvider.Message<AbstractItem> PRONOUN;
    public static final AlternativeUiTextProvider.Message<AbstractItem> TASK_NOUN;
    static final /* synthetic */ boolean $assertionsDisabled;

    public abstract Collection<? extends Job> getAllJobs();

    static {
        $assertionsDisabled = !AbstractItem.class.desiredAssertionStatus();
        LOGGER = Logger.getLogger(AbstractItem.class.getName());
        SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(AbstractItem.class.getName() + ".skipPermissionCheck");
        PRONOUN = new AlternativeUiTextProvider.Message<>();
        TASK_NOUN = new AlternativeUiTextProvider.Message<>();
    }

    protected AbstractItem(ItemGroup parent, String name) {
        this.parent = parent;
        doSetName(name);
    }

    @Exported(visibility = 999)
    @NonNull
    public String getName() {
        return this.name;
    }

    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.AbstractItem_Pronoun());
    }

    public String getTaskNoun() {
        return AlternativeUiTextProvider.get(TASK_NOUN, this, Messages.AbstractItem_TaskNoun());
    }

    @Exported
    public String getDisplayName() {
        if (null != this.displayName) {
            return this.displayName;
        }
        return getName();
    }

    @Exported
    public String getDisplayNameOrNull() {
        return this.displayName;
    }

    public void setDisplayNameOrNull(String displayName) throws IOException {
        setDisplayName(displayName);
    }

    public void setDisplayName(String displayName) throws IOException {
        this.displayName = Util.fixEmptyAndTrim(displayName);
        save();
    }

    public File getRootDir() {
        return m3getParent().getRootDirFor(this);
    }

    @NonNull
    @WithBridgeMethods(value = {Jenkins.class}, castRequired = true)
    /* renamed from: getParent, reason: merged with bridge method [inline-methods] */
    public ItemGroup m3getParent() {
        if (this.parent == null) {
            throw new IllegalStateException("no parent set on " + getClass().getName() + "[" + this.name + "]");
        }
        return this.parent;
    }

    @Exported
    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) throws IOException {
        this.description = description;
        save();
        ItemListener.fireOnUpdated(this);
    }

    protected void doSetName(String name) {
        this.name = name;
    }

    public boolean isNameEditable() {
        return false;
    }

    @RequirePOST
    @Restricted({NoExternalUse.class})
    public HttpResponse doConfirmRename(@QueryParameter String newName) throws IOException {
        String newName2 = newName == null ? null : newName.trim();
        FormValidation validationError = doCheckNewName(newName2);
        if (validationError.kind != FormValidation.Kind.OK) {
            throw new Failure(validationError.getMessage());
        }
        renameTo(newName2);
        return HttpResponses.redirectTo("../" + Functions.encode(newName2));
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public FormValidation doCheckNewName(@QueryParameter String newName) {
        if (!isNameEditable()) {
            return FormValidation.error("Trying to rename an item that does not support this operation.");
        }
        if (!hasPermission(Item.CONFIGURE)) {
            if (this.parent instanceof AccessControlled) {
                this.parent.checkPermission(Item.CREATE);
            }
            checkPermission(Item.DELETE);
        }
        String newName2 = newName == null ? null : newName.trim();
        try {
            Jenkins.checkGoodName(newName2);
            if (!$assertionsDisabled && newName2 == null) {
                throw new AssertionError();
            }
            if (newName2.equals(this.name)) {
                return FormValidation.warning(Messages.AbstractItem_NewNameUnchanged());
            }
            Jenkins.get().getProjectNamingStrategy().checkName(m3getParent().getFullName(), newName2);
            checkIfNameIsUsed(newName2);
            checkRename(newName2);
            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    private void checkIfNameIsUsed(@NonNull String newName) throws Failure {
        try {
            Item item = m3getParent().getItem(newName);
            if (item != null) {
                throw new Failure(Messages.AbstractItem_NewNameInUse(newName));
            }
            ACLContext ctx = ACL.as2(ACL.SYSTEM2);
            try {
                Item item2 = m3getParent().getItem(newName);
                if (item2 != null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Unable to rename the job {0}: name {1} is already in use. User {2} has no {3} permission for existing job with the same name", new Object[]{getFullName(), newName, ctx.getPreviousContext2().getAuthentication().getName(), Item.DISCOVER.name});
                    }
                    throw new Failure(Messages.Jenkins_NotAllowedName(newName));
                }
                if (ctx != null) {
                    ctx.close();
                }
            } finally {
            }
        } catch (AccessDeniedException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Unable to rename the job {0}: name {1} is already in use. User {2} has {3} permission, but no {4} for existing job with the same name", new Object[]{getFullName(), newName, User.current(), Item.DISCOVER.name, Item.READ.name});
            }
            throw new Failure(Messages.AbstractItem_NewNameInUse(newName));
        }
    }

    protected void checkRename(@NonNull String newName) throws Failure {
    }

    /* JADX WARN: Finally extract failed */
    @SuppressFBWarnings(value = {"SWL_SLEEP_WITH_LOCK_HELD"}, justification = "no big deal")
    protected void renameTo(final String newName) throws IOException {
        if (!isNameEditable()) {
            throw new IOException("Trying to rename an item that does not support this operation.");
        }
        ItemGroup parent = m3getParent();
        String oldName = this.name;
        String oldFullName = getFullName();
        synchronized (parent) {
            synchronized (this) {
                if (newName == null) {
                    throw new IllegalArgumentException("New name is not given");
                }
                if (this.name.equals(newName)) {
                    return;
                }
                Items.verifyItemDoesNotAlreadyExist(parent, newName, this);
                File oldRoot = getRootDir();
                doSetName(newName);
                File newRoot = getRootDir();
                boolean interrupted = false;
                boolean renamed = false;
                int retry = 0;
                while (true) {
                    if (retry >= 5) {
                        break;
                    }
                    try {
                        if (oldRoot.renameTo(newRoot)) {
                            renamed = true;
                            break;
                        } else {
                            try {
                                Thread.sleep(500L);
                            } catch (InterruptedException e) {
                                interrupted = true;
                            }
                            retry++;
                        }
                    } catch (Throwable th) {
                        if (0 == 0) {
                            doSetName(oldName);
                        }
                        throw th;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (!renamed) {
                    Copy cp = new Copy();
                    cp.setProject(new Project());
                    cp.setTodir(newRoot);
                    FileSet src = new FileSet();
                    src.setDir(oldRoot);
                    cp.addFileset(src);
                    cp.setOverwrite(true);
                    cp.setPreserveLastModified(true);
                    cp.setFailOnError(false);
                    cp.execute();
                    try {
                        Util.deleteRecursive(oldRoot);
                    } catch (IOException e2) {
                        LOGGER.log(Level.WARNING, "Ignoring IOException while deleting", (Throwable) e2);
                    }
                }
                if (1 == 0) {
                    doSetName(oldName);
                }
                parent.onRenamed(this, oldName, newName);
                ItemListener.fireLocationChange(this, oldFullName);
            }
        }
    }

    public void movedTo(DirectlyModifiableTopLevelItemGroup destination, AbstractItem newItem, File destDir) throws IOException {
        newItem.onLoad(destination, this.name);
    }

    @Exported
    @NonNull
    public final String getFullName() {
        String n = m3getParent().getFullName();
        return n.isEmpty() ? getName() : n + "/" + getName();
    }

    @Exported
    public final String getFullDisplayName() {
        String n = m3getParent().getFullDisplayName();
        return n.isEmpty() ? getDisplayName() : n + " » " + getDisplayName();
    }

    public String getRelativeDisplayNameFrom(ItemGroup p) {
        return Functions.getRelativeDisplayNameFrom(this, p);
    }

    public String getRelativeNameFromGroup(ItemGroup p) {
        return getRelativeNameFrom(p);
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        this.parent = parent;
        doSetName(name);
    }

    public void onCopiedFrom(Item src) {
    }

    public final String getUrl() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        String shortUrl = getShortUrl();
        String uri = req == null ? null : req.getRequestURI();
        if (req != null) {
            String seed = Functions.getNearestAncestorUrl(req, this);
            LOGGER.log(Level.FINER, "seed={0} for {1} from {2}", new Object[]{seed, this, uri});
            if (seed != null) {
                return seed.substring(req.getContextPath().length() + 1) + "/";
            }
            List<Ancestor> ancestors = req.getAncestors();
            if (!ancestors.isEmpty()) {
                Ancestor last = (Ancestor) ancestors.getLast();
                if (last.getObject() instanceof Widget) {
                    last = last.getPrev();
                }
                Object object = last.getObject();
                if (object instanceof View) {
                    View view = (View) object;
                    if (view.getOwner().getItemGroup() == m3getParent() && !view.isDefault()) {
                        String prefix = req.getContextPath() + "/";
                        String url = last.getUrl();
                        if (url.startsWith(prefix)) {
                            String base = url.substring(prefix.length()) + "/";
                            LOGGER.log(Level.FINER, "using {0}{1} for {2} from {3} given {4}", new Object[]{base, shortUrl, this, uri, prefix});
                            return base + shortUrl;
                        }
                        LOGGER.finer(() -> {
                            return url + " does not start with " + prefix + " as expected";
                        });
                    } else {
                        LOGGER.log(Level.FINER, "irrelevant {0} for {1} from {2}", new Object[]{view.getViewName(), this, uri});
                    }
                } else {
                    LOGGER.log(Level.FINER, "inapplicable {0} for {1} from {2}", new Object[]{last.getObject(), this, uri});
                }
            } else {
                LOGGER.log(Level.FINER, "no ancestors for {0} from {1}", new Object[]{this, uri});
            }
        } else {
            LOGGER.log(Level.FINER, "no current request for {0}", this);
        }
        String base2 = m3getParent().getUrl();
        LOGGER.log(Level.FINER, "falling back to {0}{1} for {2} from {3}", new Object[]{base2, shortUrl, this, uri});
        return base2 + shortUrl;
    }

    public String getShortUrl() {
        String prefix = m3getParent().getUrlChildPrefix();
        String subdir = Util.rawEncode(getName());
        return prefix.equals(".") ? subdir + "/" : prefix + "/" + subdir + "/";
    }

    public String getSearchUrl() {
        return getShortUrl();
    }

    @Exported(visibility = 999, name = "url")
    public final String getAbsoluteUrl() {
        return super.getAbsoluteUrl();
    }

    public final Api getApi() {
        return new Api(this);
    }

    @NonNull
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }

    public synchronized void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    public final XmlFile getConfigFile() {
        return Items.getConfigFile(this);
    }

    protected Object writeReplace() {
        return XmlFile.replaceIfNotAtTopLevel(this, () -> {
            return new Replacer(this);
        });
    }

    /* loaded from: AbstractItem$Replacer.class */
    private static class Replacer {
        private final String fullName;

        Replacer(AbstractItem i) {
            this.fullName = i.getFullName();
        }

        private Object readResolve() {
            Jenkins j = Jenkins.getInstanceOrNull();
            if (j == null) {
                return null;
            }
            return j.getItemByFullName(this.fullName);
        }
    }

    @RequirePOST
    public synchronized void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(AbstractItem.class, getClass(), "doSubmitDescription", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                doSubmitDescription(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
                return;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
        doSubmitDescriptionImpl(req, rsp);
    }

    @StaplerNotDispatchable
    @Deprecated
    public synchronized void doSubmitDescription(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doSubmitDescriptionImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doSubmitDescriptionImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        checkPermission(CONFIGURE);
        setDescription(req.getParameter("description"));
        rsp.sendRedirect(".");
    }

    @RequirePOST
    public void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, InterruptedException {
        if (Util.isOverridden(AbstractItem.class, getClass(), "doDoDelete", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            try {
                doDoDelete(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
                return;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
        doDoDeleteImpl(req, rsp);
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doDoDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException, InterruptedException {
        doDoDeleteImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doDoDeleteImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, InterruptedException {
        delete();
        if (req == null || rsp == null) {
            return;
        }
        List<Ancestor> ancestors = req.getAncestors();
        ListIterator<Ancestor> it = ancestors.listIterator(ancestors.size());
        String url = m3getParent().getUrl();
        while (true) {
            if (!it.hasPrevious()) {
                break;
            }
            Object a = it.previous().getObject();
            if (a instanceof View) {
                url = ((View) a).getUrl();
                break;
            } else if ((a instanceof ViewGroup) && a != this) {
                url = ((ViewGroup) a).getUrl();
                break;
            }
        }
        rsp.sendRedirect2(req.getContextPath() + "/" + url);
    }

    public void delete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        deleteImpl(rsp);
    }

    @Deprecated
    public void delete(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            deleteImpl(StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void deleteImpl(StaplerResponse2 rsp) throws IOException, ServletException {
        try {
            delete();
            rsp.setStatus(204);
        } catch (InterruptedException e) {
            throw new ServletException(e);
        }
    }

    public void delete() throws IOException, InterruptedException {
        checkPermission(DELETE);
        ItemListener.checkBeforeDelete(this);
        boolean responsibleForAbortingBuilds = !ItemDeletion.contains(this);
        boolean ownsRegistration = ItemDeletion.register(this);
        if (!ownsRegistration && ItemDeletion.isRegistered(this)) {
            throw new Failure(Messages.AbstractItem_BeingDeleted(getPronoun()));
        }
        if (responsibleForAbortingBuilds || ownsRegistration) {
            try {
                ItemDeletion.cancelBuildsInProgress(this);
            } finally {
                if (ownsRegistration) {
                    ItemDeletion.deregister(this);
                }
            }
        }
        if (this instanceof ItemGroup) {
            ACLContext oldContext = ACL.as2(ACL.SYSTEM2);
            try {
                Class<TopLevelItem> cls = TopLevelItem.class;
                Objects.requireNonNull(TopLevelItem.class);
                for (Item i : ((ItemGroup) this).getItems((v1) -> {
                    return r1.isInstance(v1);
                })) {
                    try {
                        i.delete();
                    } catch (IOException e) {
                        throw new IOException("Failed to delete " + i.getFullDisplayName(), e);
                    } catch (AbortException e2) {
                        throw new AbortException("Failed to delete " + i.getFullDisplayName() + " : " + e2.getMessage()).initCause(e2);
                    }
                }
                if (oldContext != null) {
                    oldContext.close();
                }
            } catch (Throwable th) {
                if (oldContext != null) {
                    try {
                        oldContext.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        synchronized (this) {
            performDelete();
        }
        SaveableListener.fireOnDeleted(this, getConfigFile());
        m3getParent().onDeleted(this);
        Jenkins.get().rebuildDependencyGraphAsync();
    }

    protected void performDelete() throws IOException, InterruptedException {
        getConfigFile().delete();
        Util.deleteRecursive(getRootDir());
    }

    @WebMethod(name = {"config.xml"})
    public void doConfigDotXml(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (Util.isOverridden(AbstractItem.class, getClass(), "doConfigDotXml", new Class[]{StaplerRequest.class, StaplerResponse.class})) {
            doConfigDotXml(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doConfigDotXmlImpl(req, rsp);
        }
    }

    @StaplerNotDispatchable
    @Deprecated
    public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doConfigDotXmlImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doConfigDotXmlImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.getMethod().equals("GET")) {
            rsp.setContentType("application/xml");
            writeConfigDotXml(rsp.getOutputStream());
        } else if (req.getMethod().equals("POST")) {
            updateByXml((Source) new StreamSource(req.getReader()));
        } else {
            rsp.sendError(400);
        }
    }

    @Restricted({NoExternalUse.class})
    public void writeConfigDotXml(OutputStream os) throws IOException {
        checkPermission(EXTENDED_READ);
        XmlFile configFile = getConfigFile();
        if (hasPermission(CONFIGURE)) {
            Items.XSTREAM2.toXMLUTF8(this, os);
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Items.XSTREAM2.toXMLUTF8(this, baos);
        String xml = baos.toString(StandardCharsets.UTF_8);
        String encoding = configFile.sniffEncoding();
        Iterator it = ExtendedReadRedaction.all().iterator();
        while (it.hasNext()) {
            ExtendedReadRedaction redaction = (ExtendedReadRedaction) it.next();
            LOGGER.log(Level.FINE, () -> {
                return "Applying redaction " + redaction.getClass().getName();
            });
            xml = redaction.apply(xml);
        }
        IOUtils.write(xml, os, encoding);
    }

    @Deprecated
    public void updateByXml(StreamSource source) throws IOException {
        updateByXml((Source) source);
    }

    public void updateByXml(Source source) throws IOException {
        checkPermission(CONFIGURE);
        XmlFile configXmlFile = getConfigFile();
        StringWriter out = new StringWriter();
        try {
            XMLUtils.safeTransform(source, new StreamResult(out));
            out.close();
            Object o = Items.XSTREAM2.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(out.getBuffer().toString())), this, null, true);
            if (o != this) {
                throw new IOException("Expecting " + String.valueOf(getClass()) + " but got " + String.valueOf(o.getClass()) + " instead");
            }
            Items.runWhileUpdatingByXml(() -> {
                onLoad(m3getParent(), getRootDir().getName());
            });
            Jenkins.get().rebuildDependencyGraphAsync();
            configXmlFile.write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
            ItemListener.fireOnUpdated(this);
        } catch (TransformerException | SAXException e) {
            throw new IOException("Failed to process config.xml", e);
        }
    }

    @RequirePOST
    public void doReload() throws IOException {
        load();
    }

    public void load() throws IOException {
        checkPermission(CONFIGURE);
        getConfigFile().unmarshal(this);
        Items.runWhileUpdatingByXml(() -> {
            onLoad(m3getParent(), m3getParent().getItemName(getRootDir(), this));
        });
        Jenkins.get().rebuildDependencyGraphAsync();
    }

    public String getSearchName() {
        return getName();
    }

    public String toString() {
        return super/*java.lang.Object*/.toString() + "[" + (this.parent != null ? getFullName() : "?/" + this.name) + "]";
    }

    @Restricted({NoExternalUse.class})
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            if (!hasPermission(Item.DISCOVER)) {
                return null;
            }
            checkPermission(Item.READ);
        }
        return this;
    }

    @CLIResolver
    public static AbstractItem resolveForCLI(@Argument(required = true, metaVar = "NAME", usage = "Item name") String name) throws CmdLineException {
        AbstractItem item = (AbstractItem) Jenkins.get().getItemByFullName(name, AbstractItem.class);
        if (item == null) {
            AbstractItem project = (AbstractItem) Items.findNearest(AbstractItem.class, name, Jenkins.get());
            throw new CmdLineException((CmdLineParser) null, project == null ? Messages.AbstractItem_NoSuchJobExistsWithoutSuggestion(name) : Messages.AbstractItem_NoSuchJobExists(name, project.getFullName()));
        }
        return item;
    }
}
