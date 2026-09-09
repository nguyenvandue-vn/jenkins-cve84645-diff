package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor;
import hudson.model.listeners.ItemListener;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.views.ListViewColumn;
import hudson.views.StatusFilter;
import hudson.views.ViewJobFilter;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;

/* loaded from: ListView.class */
public class ListView extends View implements DirectlyModifiableView {

    @GuardedBy("this")
    SortedSet<String> jobNames;
    private DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> jobFilters;
    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;
    private String includeRegex;
    private volatile boolean recurse;
    private transient Pattern includePattern;

    @Deprecated
    private transient Boolean statusFilter;

    @DataBoundConstructor
    public ListView(String name) {
        super(name);
        this.jobNames = new TreeSet(String.CASE_INSENSITIVE_ORDER);
        initColumns();
        initJobFilters();
    }

    public ListView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }

    @DataBoundSetter
    public void setColumns(List<ListViewColumn> columns) throws IOException {
        this.columns.replaceBy(columns);
    }

    @DataBoundSetter
    public void setJobFilters(List<ViewJobFilter> jobFilters) throws IOException {
        this.jobFilters.replaceBy(jobFilters);
    }

    protected Object readResolve() {
        if (this.includeRegex != null) {
            try {
                this.includePattern = Pattern.compile(this.includeRegex);
            } catch (PatternSyntaxException x) {
                this.includeRegex = null;
                OldDataMonitor.report(this, Set.of(x));
            }
        }
        synchronized (this) {
            if (this.jobNames == null) {
                this.jobNames = new TreeSet(String.CASE_INSENSITIVE_ORDER);
            }
        }
        initColumns();
        initJobFilters();
        if (this.statusFilter != null) {
            this.jobFilters.add(new StatusFilter(this.statusFilter.booleanValue()));
        }
        return this;
    }

    protected void initColumns() {
        if (this.columns == null) {
            this.columns = new DescribableList<>(this, ListViewColumn.createDefaultInitialColumnList(getClass()));
        }
    }

    protected void initJobFilters() {
        if (this.jobFilters == null) {
            this.jobFilters = new DescribableList<>(this);
        }
    }

    public boolean hasJobFilterExtensions() {
        return !ViewJobFilter.all().isEmpty();
    }

    public DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> getJobFilters() {
        return this.jobFilters;
    }

    /* renamed from: getColumns, reason: merged with bridge method [inline-methods] */
    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> m34getColumns() {
        return this.columns;
    }

    public synchronized Set<String> getJobNames() {
        return Collections.unmodifiableSet(this.jobNames);
    }

    /* renamed from: getItems, reason: merged with bridge method [inline-methods] */
    public List<TopLevelItem> m35getItems() {
        return getItems(this.recurse);
    }

    private List<TopLevelItem> getItems(boolean recurse) {
        SortedSet<String> names;
        List<TopLevelItem> items = new ArrayList<>();
        synchronized (this) {
            names = new TreeSet<>(this.jobNames);
        }
        ItemGroup<? extends TopLevelItem> parent = getOwner().getItemGroup();
        if (recurse) {
            if (!names.isEmpty() || this.includePattern != null) {
                items.addAll(parent.getAllItems(TopLevelItem.class, item -> {
                    String itemName = item.getRelativeNameFrom(parent);
                    if (names.contains(itemName)) {
                        return true;
                    }
                    if (this.includePattern != null) {
                        return this.includePattern.matcher(itemName).matches();
                    }
                    return false;
                }));
            }
        } else {
            for (String name : names) {
                try {
                    TopLevelItem i = parent.getItem(name);
                    if (i != null) {
                        items.add(i);
                    }
                } catch (AccessDeniedException e) {
                }
            }
            if (this.includePattern != null) {
                items.addAll(parent.getItems(item2 -> {
                    String itemName = item2.getRelativeNameFrom(parent);
                    return this.includePattern.matcher(itemName).matches();
                }));
            }
        }
        DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> jobFilters = getJobFilters();
        if (!jobFilters.isEmpty()) {
            List<TopLevelItem> candidates = recurse ? parent.getAllItems(TopLevelItem.class) : new ArrayList<>(parent.getItems());
            Iterator it = jobFilters.iterator();
            while (it.hasNext()) {
                ViewJobFilter jobFilter = (ViewJobFilter) it.next();
                items = jobFilter.filter(items, candidates, this);
            }
        }
        return new ArrayList<>(new LinkedHashSet(items));
    }

    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = new SearchIndexBuilder().addAllAnnotations(this);
        makeSearchIndex(sib);
        addDisplayNamesToSearchIndex(sib, getItems(true));
        return sib;
    }

    public boolean contains(TopLevelItem item) {
        return m35getItems().contains(item);
    }

    public synchronized boolean jobNamesContains(TopLevelItem item) {
        if (item == null) {
            return false;
        }
        return this.jobNames.contains(item.getRelativeNameFrom(getOwner().getItemGroup()));
    }

    public void add(TopLevelItem item) throws IOException {
        synchronized (this) {
            this.jobNames.add(item.getRelativeNameFrom(getOwner().getItemGroup()));
        }
        save();
    }

    public boolean remove(TopLevelItem item) throws IOException {
        synchronized (this) {
            String name = item.getRelativeNameFrom(getOwner().getItemGroup());
            if (!this.jobNames.remove(name)) {
                return false;
            }
            save();
            return true;
        }
    }

    public String getIncludeRegex() {
        return this.includeRegex;
    }

    public boolean isRecurse() {
        return this.recurse;
    }

    @DataBoundSetter
    public void setRecurse(boolean recurse) {
        this.recurse = recurse;
    }

    @Deprecated
    public Boolean getStatusFilter() {
        return this.statusFilter;
    }

    @Restricted({NoExternalUse.class})
    public boolean isAddToCurrentView() {
        boolean z;
        synchronized (this) {
            z = !this.jobNames.isEmpty() || (this.jobFilters.isEmpty() && this.includePattern == null);
        }
        return z;
    }

    private boolean needToAddToCurrentView(StaplerRequest2 req) throws ServletException {
        String json = req.getParameter("json");
        if (json != null && !json.isEmpty()) {
            JSONObject form = req.getSubmittedForm();
            return form.has("addToCurrentView") && form.getBoolean("addToCurrentView");
        }
        return true;
    }

    @POST
    public Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        ModifiableItemGroup itemGroup = getOwner().getItemGroup();
        if (itemGroup instanceof ModifiableItemGroup) {
            TopLevelItem item = itemGroup.doCreateItem(req, rsp);
            if (item != null && needToAddToCurrentView(req)) {
                synchronized (this) {
                    this.jobNames.add(item.getRelativeNameFrom(getOwner().getItemGroup()));
                }
                this.owner.save();
            }
            return item;
        }
        return null;
    }

    @RequirePOST
    public HttpResponse doAddJobToView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if (name == null) {
            throw new Failure("Query parameter 'name' is required");
        }
        TopLevelItem item = resolveName(name);
        if (item == null) {
            throw new Failure("Query parameter 'name' does not correspond to a known item");
        }
        if (contains(item)) {
            return HttpResponses.ok();
        }
        add(item);
        this.owner.save();
        return HttpResponses.ok();
    }

    @RequirePOST
    public HttpResponse doRemoveJobFromView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if (name == null) {
            throw new Failure("Query parameter 'name' is required");
        }
        TopLevelItem item = resolveName(name);
        if (item == null) {
            throw new Failure("Query parameter 'name' does not correspond to a known and readable item");
        }
        if (remove(item)) {
            this.owner.save();
        }
        return HttpResponses.ok();
    }

    @CheckForNull
    private TopLevelItem resolveName(String name) {
        TopLevelItem item = getOwner().getItemGroup().getItem(name);
        if (item == null) {
            item = (TopLevelItem) Jenkins.get().getItemByFullName(Items.getCanonicalName(getOwner().getItemGroup(), name), TopLevelItem.class);
        }
        return item;
    }

    protected void submit(StaplerRequest2 req) throws ServletException, Descriptor.FormException, IOException {
        if (Util.isOverridden(View.class, getClass(), "submit", new Class[]{StaplerRequest.class})) {
            try {
                submit(StaplerRequest.fromStaplerRequest2(req));
                return;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        }
        submitImpl(req);
    }

    @Deprecated
    protected void submit(StaplerRequest req) throws javax.servlet.ServletException, Descriptor.FormException, IOException {
        try {
            submitImpl(StaplerRequest.toStaplerRequest2(req));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void submitImpl(StaplerRequest2 req) throws ServletException, Descriptor.FormException, IOException {
        Iterable<? extends TopLevelItem> items;
        JSONObject json = req.getSubmittedForm();
        synchronized (this) {
            this.recurse = json.optBoolean("recurse", true);
            this.jobNames.clear();
            if (this.recurse) {
                items = getOwner().getItemGroup().getAllItems(TopLevelItem.class);
            } else {
                items = getOwner().getItemGroup().getItems();
            }
            for (TopLevelItem item : items) {
                String relativeNameFrom = item.getRelativeNameFrom(getOwner().getItemGroup());
                if (req.getParameter("item_" + relativeNameFrom) != null) {
                    this.jobNames.add(relativeNameFrom);
                }
            }
        }
        setIncludeRegex(req.getParameter("useincluderegex") != null ? req.getParameter("includeRegex") : null);
        if (this.columns == null) {
            this.columns = new DescribableList<>(this);
        }
        this.columns.rebuildHetero(req, json, ListViewColumn.all(), "columns");
        if (this.jobFilters == null) {
            this.jobFilters = new DescribableList<>(this);
        }
        this.jobFilters.rebuildHetero(req, json, ViewJobFilter.all(), "jobFilters");
        String filter = Util.fixEmpty(req.getParameter("statusFilter"));
        this.statusFilter = filter != null ? Boolean.valueOf("1".equals(filter)) : null;
    }

    @DataBoundSetter
    public void setIncludeRegex(String includeRegex) {
        this.includeRegex = Util.nullify(includeRegex);
        if (this.includeRegex == null) {
            this.includePattern = null;
        } else {
            this.includePattern = Pattern.compile(includeRegex);
        }
    }

    @DataBoundSetter
    public synchronized void setJobNames(Set<String> jobNames) {
        this.jobNames = new TreeSet(jobNames);
    }

    @DataBoundSetter
    @Deprecated
    public void setStatusFilter(Boolean statusFilter) {
        this.statusFilter = statusFilter;
    }

    @Extension
    @Symbol({"list"})
    /* loaded from: ListView$DescriptorImpl.class */
    public static class DescriptorImpl extends ViewDescriptor {
        @NonNull
        public String getDisplayName() {
            return Messages.ListView_DisplayName();
        }

        public FormValidation doCheckIncludeRegex(@QueryParameter String value) throws IOException, ServletException, InterruptedException {
            String v = Util.fixEmpty(value);
            if (v != null) {
                try {
                    Pattern.compile(v);
                } catch (PatternSyntaxException pse) {
                    return FormValidation.error(pse.getMessage());
                }
            }
            return FormValidation.ok();
        }
    }

    @Deprecated
    public static List<ListViewColumn> getDefaultColumns() {
        return ListViewColumn.createDefaultInitialColumnList(ListView.class);
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: ListView$Listener.class */
    public static final class Listener extends ItemListener {
        public void onLocationChanged(final Item item, final String oldFullName, final String newFullName) {
            ACLContext acl = ACL.as2(ACL.SYSTEM2);
            try {
                locationChanged(oldFullName, newFullName);
                if (acl != null) {
                    acl.close();
                }
            } catch (Throwable th) {
                if (acl != null) {
                    try {
                        acl.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }

        private void locationChanged(String oldFullName, String newFullName) {
            Jenkins jenkins2 = Jenkins.get();
            locationChanged(jenkins2, oldFullName, newFullName);
            for (Item g : jenkins2.allItems()) {
                if (g instanceof ViewGroup) {
                    locationChanged((ViewGroup) g, oldFullName, newFullName);
                }
            }
        }

        private void locationChanged(ViewGroup vg, String oldFullName, String newFullName) {
            for (View v : vg.getViews()) {
                if (v instanceof ListView) {
                    renameViewItem(oldFullName, newFullName, vg, (ListView) v);
                }
                if (v instanceof ViewGroup) {
                    locationChanged((ViewGroup) v, oldFullName, newFullName);
                }
            }
        }

        private void renameViewItem(String oldFullName, String newFullName, ViewGroup vg, ListView lv) {
            boolean needsSave;
            synchronized (lv) {
                Set<String> oldJobNames = new HashSet<>(lv.jobNames);
                lv.jobNames.clear();
                for (String oldName : oldJobNames) {
                    lv.jobNames.add(Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, oldName, vg.getItemGroup()));
                }
                needsSave = !oldJobNames.equals(lv.jobNames);
            }
            if (needsSave) {
                try {
                    lv.save();
                } catch (IOException x) {
                    Logger.getLogger(ListView.class.getName()).log(Level.WARNING, (String) null, (Throwable) x);
                }
            }
        }

        public void onDeleted(final Item item) {
            ACLContext acl = ACL.as2(ACL.SYSTEM2);
            try {
                deleted(item);
                if (acl != null) {
                    acl.close();
                }
            } catch (Throwable th) {
                if (acl != null) {
                    try {
                        acl.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }

        private void deleted(Item item) {
            Jenkins jenkins2 = Jenkins.get();
            deleted(jenkins2, item);
            for (Item g : jenkins2.allItems()) {
                if (g instanceof ViewGroup) {
                    deleted((ViewGroup) g, item);
                }
            }
        }

        private void deleted(ViewGroup vg, Item item) {
            for (View v : vg.getViews()) {
                if (v instanceof ListView) {
                    deleteViewItem(item, vg, (ListView) v);
                }
                if (v instanceof ViewGroup) {
                    deleted((ViewGroup) v, item);
                }
            }
        }

        private void deleteViewItem(Item item, ViewGroup vg, ListView lv) {
            boolean needsSave;
            synchronized (lv) {
                needsSave = lv.jobNames.remove(item.getRelativeNameFrom(vg.getItemGroup()));
            }
            if (needsSave) {
                try {
                    lv.save();
                } catch (IOException x) {
                    Logger.getLogger(ListView.class.getName()).log(Level.WARNING, (String) null, (Throwable) x);
                }
            }
        }
    }
}
