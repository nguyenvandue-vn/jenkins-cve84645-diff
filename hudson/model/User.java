package hudson.model;

import com.infradna.tool.bridge_method_injector.BridgeMethodsAdded;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Cause;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import hudson.tasks.UserAvatarResolver;
import hudson.util.FormValidation;
import hudson.util.RunList;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.scm.RunWithSCM;
import jenkins.search.SearchGroup;
import jenkins.security.HMACConfidentialKey;
import jenkins.security.ImpersonatingUserDetailsService2;
import jenkins.security.UserDetailsCache;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExportedBean
@BridgeMethodsAdded
/* loaded from: User.class */
public class User extends AbstractModelObject implements AccessControlled, DescriptorByNameOwner, Loadable, Saveable, Comparable<User>, ModelObjectWithContextMenu, StaplerProxy, PersistenceRoot {
    static final String CONFIG_XML = "config.xml";
    String id;
    private volatile String fullName;
    private volatile String description;
    private static final int PREFIX_MAX = 14;
    private static final Pattern DISALLOWED_PREFIX_CHARS;
    static final Pattern HASHED_DIRNAMES;
    private static final HMACConfidentialKey DIRNAMES;
    public static final XStream2 XSTREAM = new XStream2();
    private static final Logger LOGGER = Logger.getLogger(User.class.getName());

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(User.class.getName() + ".skipPermissionCheck");

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean ALLOW_NON_EXISTENT_USER_TO_LOGIN = SystemProperties.getBoolean(User.class.getName() + ".allowNonExistentUserToLogin");

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    public static boolean ALLOW_USER_CREATION_VIA_URL = SystemProperties.getBoolean(User.class.getName() + ".allowUserCreationViaUrl");
    private static final String UNKNOWN_USERNAME = "unknown";
    private static final String[] ILLEGAL_PERSISTED_USERNAMES = {"anonymous", "SYSTEM", UNKNOWN_USERNAME};

    @SuppressFBWarnings(value = {"SS_SHOULD_BE_STATIC"}, justification = "Reserved for future use")
    private final int version = 10;
    private volatile List<UserProperty> properties = new ArrayList();

    static {
        XSTREAM.alias("user", User.class);
        DISALLOWED_PREFIX_CHARS = Pattern.compile("[^A-Za-z0-9]");
        HASHED_DIRNAMES = Pattern.compile("[a-z0-9]{0,14}_[a-f0-9]{64}");
        DIRNAMES = new HMACConfidentialKey(User.class, "DIRNAMES");
    }

    private User() {
    }

    private User(String id, String fullName) {
        this.id = id;
        this.fullName = fullName;
        load(id);
    }

    public void load() {
        load(this.id);
    }

    private void load(String userId) {
        clearExistingProperties();
        loadFromUserConfigFile(userId);
        fixUpAfterLoad();
    }

    private void fixUpAfterLoad() {
        removeNullsThatFailedToLoad();
        allocateDefaultPropertyInstancesAsNeeded();
        setUserToProperties();
    }

    private void setUserToProperties() {
        for (UserProperty p : this.properties) {
            p.setUser(this);
        }
    }

    private void allocateDefaultPropertyInstancesAsNeeded() {
        UserProperty up;
        Iterator it = UserProperty.all().iterator();
        while (it.hasNext()) {
            UserPropertyDescriptor d = (UserPropertyDescriptor) it.next();
            if (getProperty(d.clazz) == null && (up = d.newInstance(this)) != null) {
                this.properties.add(up);
            }
        }
    }

    private void removeNullsThatFailedToLoad() {
        this.properties.removeIf((v0) -> {
            return Objects.isNull(v0);
        });
    }

    private void loadFromUserConfigFile(String userId) {
        AllUsers.getInstance().migrateUserIdMapper();
        XmlFile config = getConfigFile();
        try {
            if (config.exists()) {
                config.unmarshal(this);
                this.id = userId;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load " + String.valueOf(config), (Throwable) e);
        }
    }

    private void clearExistingProperties() {
        this.properties.clear();
    }

    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(getUserFolderFor(this.id), CONFIG_XML));
    }

    @NonNull
    public static IdStrategy idStrategy() {
        Jenkins j = Jenkins.get();
        SecurityRealm realm = j.getSecurityRealm();
        if (realm == null) {
            return IdStrategy.CASE_INSENSITIVE;
        }
        return realm.getUserIdStrategy();
    }

    @Override // java.lang.Comparable
    public int compareTo(@NonNull User that) {
        return idStrategy().compare(this.id, that.id);
    }

    @Exported
    public String getId() {
        return this.id;
    }

    @NonNull
    public String getUrl() {
        return "user/" + Util.rawEncode(idStrategy().keyFor(this.id));
    }

    @NonNull
    public String getSearchUrl() {
        return "/user/" + Util.rawEncode(idStrategy().keyFor(this.id));
    }

    public String getSearchIcon() {
        return UserAvatarResolver.resolve(this, "48x48");
    }

    public SearchGroup getSearchGroup() {
        return SearchGroup.get(SearchGroup.UserSearchGroup.class);
    }

    @Exported(visibility = 999)
    @NonNull
    public String getAbsoluteUrl() {
        return Jenkins.get().getRootUrl() + getUrl();
    }

    @Exported(visibility = 999)
    @NonNull
    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String name) {
        if (Util.fixEmptyAndTrim(name) == null) {
            name = this.id;
        }
        this.fullName = name;
    }

    @Exported
    @CheckForNull
    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<Descriptor<UserProperty>, UserProperty> getProperties() {
        return Descriptor.toMap(this.properties);
    }

    public synchronized void addProperty(@NonNull UserProperty p) throws IOException {
        UserProperty old = getProperty(p.getClass());
        List<UserProperty> ps = new ArrayList<>(this.properties);
        if (old != null) {
            ps.remove(old);
        }
        ps.add(p);
        p.setUser(this);
        this.properties = ps;
        save();
    }

    public synchronized void addProperties(@NonNull List<UserProperty> multipleProperties) throws IOException {
        List<UserProperty> newProperties = new ArrayList<>(this.properties);
        for (UserProperty property : multipleProperties) {
            UserProperty oldProp = getProperty(property.getClass());
            if (oldProp != null) {
                newProperties.remove(oldProp);
            }
            newProperties.add(property);
            property.setUser(this);
        }
        this.properties = newProperties;
        save();
    }

    @Exported(name = "property", inline = true)
    public List<UserProperty> getAllProperties() {
        if (hasPermission(Jenkins.ADMINISTER)) {
            return Collections.unmodifiableList(this.properties);
        }
        return Collections.emptyList();
    }

    public <T extends UserProperty> T getProperty(Class<T> clazz) {
        for (UserProperty p : this.properties) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    @NonNull
    public Authentication impersonate2() throws UsernameNotFoundException {
        return impersonate(getUserDetailsForImpersonation2());
    }

    @NonNull
    @Deprecated
    public org.acegisecurity.Authentication impersonate() throws org.acegisecurity.userdetails.UsernameNotFoundException {
        try {
            return org.acegisecurity.Authentication.fromSpring(impersonate2());
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    @NonNull
    public UserDetails getUserDetailsForImpersonation2() throws UsernameNotFoundException {
        ImpersonatingUserDetailsService2 userDetailsService = new ImpersonatingUserDetailsService2(Jenkins.get().getSecurityRealm().getSecurityComponents().userDetails2);
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(this.id);
            LOGGER.log(Level.FINE, "Impersonation of the user {0} was a success", this.id);
            return userDetails;
        } catch (UsernameNotFoundException e) {
            if (ALLOW_NON_EXISTENT_USER_TO_LOGIN) {
                LOGGER.log(Level.FINE, "The user {0} was not found in the SecurityRealm but we are required to let it pass, due to ALLOW_NON_EXISTENT_USER_TO_LOGIN", this.id);
                return new LegitimateButUnknownUserDetails(this.id);
            }
            LOGGER.log(Level.FINE, "The user {0} was not found in the SecurityRealm", this.id);
            throw e;
        } catch (UserMayOrMayNotExistException2 e2) {
            LOGGER.log(Level.FINE, "The user {0} may or may not exist in the SecurityRealm, so we provide minimum access", this.id);
            return new LegitimateButUnknownUserDetails(this.id);
        }
    }

    @NonNull
    @Deprecated
    public org.acegisecurity.userdetails.UserDetails getUserDetailsForImpersonation() throws org.acegisecurity.userdetails.UsernameNotFoundException {
        try {
            return org.acegisecurity.userdetails.UserDetails.fromSpring(getUserDetailsForImpersonation2());
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    @NonNull
    @Restricted({NoExternalUse.class})
    public Authentication impersonate(@NonNull UserDetails userDetails) {
        return new UsernamePasswordAuthenticationToken(userDetails.getUsername(), "", userDetails.getAuthorities());
    }

    @RequirePOST
    public void doSubmitDescription(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        this.description = req.getParameter("description");
        save();
        rsp.sendRedirect(".");
    }

    @NonNull
    public static User getUnknown() {
        return getById(UNKNOWN_USERNAME, true);
    }

    @Nullable
    @Deprecated
    public static User get(String idOrFullName, boolean create) {
        return get(idOrFullName, create, Collections.emptyMap());
    }

    @Nullable
    public static User get(String idOrFullName, boolean create, @NonNull Map context) {
        if (idOrFullName == null) {
            return null;
        }
        User user = AllUsers.get(idOrFullName);
        if (user != null) {
            return user;
        }
        String id = CanonicalIdResolver.resolve(idOrFullName, context);
        return getOrCreateById(id, idOrFullName, create);
    }

    @Nullable
    private static User getOrCreateById(@NonNull String id, @NonNull String fullName, boolean create) {
        User u = AllUsers.get(id);
        if (u == null && create) {
            u = new User(id, fullName);
            AllUsers.put(id, u);
            if (!id.equals(fullName)) {
                try {
                    u.save();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "Failed to save user configuration for " + id, (Throwable) x);
                }
            }
        }
        return u;
    }

    @NonNull
    @Deprecated
    public static User get(String idOrFullName) {
        return getOrCreateByIdOrFullName(idOrFullName);
    }

    @NonNull
    public static User getOrCreateByIdOrFullName(@NonNull String idOrFullName) {
        return get(idOrFullName, true, Collections.emptyMap());
    }

    @CheckForNull
    public static User current() {
        return get2(Jenkins.getAuthentication2());
    }

    @CheckForNull
    public static User get2(@CheckForNull Authentication a) {
        if (a == null || (a instanceof AnonymousAuthenticationToken)) {
            return null;
        }
        return getById(a.getName(), true);
    }

    @CheckForNull
    @Deprecated
    public static User get(@CheckForNull org.acegisecurity.Authentication a) {
        return get2(a != null ? a.toSpring() : null);
    }

    @Nullable
    public static User getById(String id, boolean create) {
        return getOrCreateById(id, id, create);
    }

    @NonNull
    public static Collection<User> getAll() {
        IdStrategy strategy = idStrategy();
        ArrayList<User> users = new ArrayList<>(AllUsers.values());
        users.sort((o1, o2) -> {
            return strategy.compare(o1.getId(), o2.getId());
        });
        return users;
    }

    @Restricted({Beta.class})
    public static void reload() throws IOException {
        AllUsers.reload();
    }

    public static void rekey() {
        try {
            File[] subdirectories = getUsersDirectory().listFiles();
            if (subdirectories != null) {
                for (File oldDirectory : subdirectories) {
                    String dirName = oldDirectory.getName();
                    if (HASHED_DIRNAMES.matcher(dirName).matches()) {
                        XmlFile xml = new XmlFile(XSTREAM, new File(oldDirectory, CONFIG_XML));
                        if (xml.exists()) {
                            try {
                                User user = (User) xml.read();
                                if (user.id != null) {
                                    File newDirectory = getUserFolderFor(user.id);
                                    if (!oldDirectory.equals(newDirectory)) {
                                        Files.move(oldDirectory.toPath(), newDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                        LOGGER.info(() -> {
                                            return "migrated " + String.valueOf(oldDirectory) + " to " + String.valueOf(newDirectory);
                                        });
                                    }
                                }
                            } catch (Exception x) {
                                LOGGER.log(Level.WARNING, "failed to migrate " + String.valueOf(xml), (Throwable) x);
                            }
                        }
                    }
                }
            }
            reload();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to perform rekey operation.", (Throwable) e);
        }
    }

    @NonNull
    public String getDisplayName() {
        return getFullName();
    }

    private boolean relatedTo(@NonNull Run<?, ?> b) {
        String userId;
        if ((b instanceof RunWithSCM) && ((RunWithSCM) b).hasParticipant(this)) {
            return true;
        }
        for (Cause cause : b.getCauses()) {
            if ((cause instanceof Cause.UserIdCause) && (userId = ((Cause.UserIdCause) cause).getUserId()) != null && idStrategy().equals(userId, getId())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @WithBridgeMethods({List.class})
    /* renamed from: getBuilds, reason: merged with bridge method [inline-methods] */
    public RunList m56getBuilds() {
        return RunList.fromJobs(Jenkins.get().allItems(Job.class)).filter(this::relatedTo);
    }

    @NonNull
    public Set<AbstractProject<?, ?>> getProjects() {
        Set<AbstractProject<?, ?>> r = new HashSet<>();
        for (AbstractProject<?, ?> p : Jenkins.get().allItems(AbstractProject.class, p2 -> {
            return p2.hasParticipant(this);
        })) {
            r.add(p);
        }
        return r;
    }

    public String toString() {
        return this.id;
    }

    @Deprecated
    public static void clear() {
        if (ExtensionList.lookup(AllUsers.class).isEmpty()) {
            return;
        }
        AllUsers.clear();
    }

    @CheckForNull
    public File getUserFolder() {
        File d = getUserFolderFor(this.id);
        if (d.isDirectory()) {
            return d;
        }
        return null;
    }

    static File getUsersDirectory() {
        return new File(Jenkins.get().getRootDir(), "users");
    }

    public File getRootDir() {
        return getUserFolderFor(this.id);
    }

    private static String getUserFolderNameFor(String id) {
        String fullPrefix = DISALLOWED_PREFIX_CHARS.matcher(id).replaceAll("").toLowerCase(Locale.ROOT);
        return (fullPrefix.length() > PREFIX_MAX ? fullPrefix.substring(0, PREFIX_MAX) : fullPrefix) + "_" + DIRNAMES.mac(idStrategy().keyFor(id));
    }

    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN"}, justification = "sanitized")
    static File getUserFolderFor(String id) {
        return new File(getUsersDirectory(), getUserFolderNameFor(id));
    }

    public static boolean isIdOrFullnameAllowed(@CheckForNull String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String trimmedId = id.trim();
        for (String invalidId : ILLEGAL_PERSISTED_USERNAMES) {
            if (trimmedId.equalsIgnoreCase(invalidId)) {
                return false;
            }
        }
        return true;
    }

    public synchronized void save() throws IOException {
        if (!isIdOrFullnameAllowed(this.id)) {
            throw FormValidation.error(Messages.User_IllegalUsername(this.id));
        }
        if (!isIdOrFullnameAllowed(this.fullName)) {
            throw FormValidation.error(Messages.User_IllegalFullname(this.fullName));
        }
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile xmlFile = getConfigFile();
        xmlFile.write(this);
        SaveableListener.fireOnChange(this, xmlFile);
    }

    public void delete() throws IOException {
        String idKey = idStrategy().keyFor(this.id);
        AllUsers.remove(this.id);
        Util.deleteRecursive(getUserFolderFor(this.id));
        UserDetailsCache.get().invalidate(idKey);
    }

    public Api getApi() {
        return new Api(this);
    }

    @RequirePOST
    public void doDoDelete(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        if (idStrategy().equals(this.id, Jenkins.getAuthentication2().getName())) {
            rsp.sendError(400, "Cannot delete self");
        } else {
            delete();
            rsp.sendRedirect2("../..");
        }
    }

    public void doRssAll(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (all builds)", getUrl(), m56getBuilds().newBuilds());
    }

    public void doRssFailed(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (failed builds)", getUrl(), m56getBuilds().regressionOnly());
    }

    public void doRssLatest(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        List<Run> lastBuilds = new ArrayList<>();
        for (Job<?, ?> p : Jenkins.get().allItems(Job.class)) {
            Run<?, ?> mo6getLastBuild = p.mo6getLastBuild();
            while (true) {
                Run<?, ?> b = mo6getLastBuild;
                if (b == null) {
                    break;
                }
                if (!relatedTo(b)) {
                    mo6getLastBuild = b.getPreviousBuild();
                } else {
                    lastBuilds.add(b);
                    break;
                }
            }
        }
        lastBuilds.sort((o1, o2) -> {
            return Items.BY_FULL_NAME.compare(o1.getParent(), o2.getParent());
        });
        RSS.rss(req, rsp, "Jenkins:" + getDisplayName() + " (latest builds)", getUrl(), RunList.fromRuns(lastBuilds), Run.FEED_ADAPTER_LATEST);
    }

    @NonNull
    public ACL getACL() {
        ACL base = Jenkins.get().getAuthorizationStrategy().getACL(this);
        return ACL.lambda2((a, permission) -> {
            return Boolean.valueOf((idStrategy().equals(a.getName(), this.id) && !(a instanceof AnonymousAuthenticationToken)) || base.hasPermission2(a, permission));
        });
    }

    public boolean canDelete() {
        IdStrategy strategy = idStrategy();
        return (!hasPermission(Jenkins.ADMINISTER) || strategy.equals(this.id, Jenkins.getAuthentication2().getName()) || getUserFolder() == null) ? false : true;
    }

    @NonNull
    public List<String> getAuthorities() {
        String n;
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return Collections.emptyList();
        }
        List<String> r = new ArrayList<>();
        try {
            Authentication authentication = impersonate2();
            for (GrantedAuthority a : authentication.getAuthorities()) {
                if (!a.equals(SecurityRealm.AUTHENTICATED_AUTHORITY2) && (n = a.getAuthority()) != null && !idStrategy().equals(n, this.id)) {
                    r.add(n);
                }
            }
            r.sort(String.CASE_INSENSITIVE_ORDER);
            return r;
        } catch (UsernameNotFoundException e) {
            LOGGER.log(Level.FINE, "cannot look up authorities for " + this.id, e);
            return Collections.emptyList();
        }
    }

    public Object getDynamic(String token) {
        for (Action action : getTransientActions()) {
            if (Objects.equals(action.getUrlName(), token)) {
                return action;
            }
        }
        for (Action action2 : getPropertyActions()) {
            if (Objects.equals(action2.getUrlName(), token)) {
                return action2;
            }
        }
        return null;
    }

    public List<Action> getPropertyActions() {
        List<Action> actions = new ArrayList<>();
        Iterator<UserProperty> it = getProperties().values().iterator();
        while (it.hasNext()) {
            Action action = (UserProperty) it.next();
            if (action instanceof Action) {
                actions.add(action);
            }
        }
        return Collections.unmodifiableList(actions);
    }

    public List<Action> getTransientActions() {
        List<Action> actions = new ArrayList<>();
        Iterator it = TransientUserActionFactory.all().iterator();
        while (it.hasNext()) {
            TransientUserActionFactory factory = (TransientUserActionFactory) it.next();
            actions.addAll(factory.createFor(this));
        }
        return Collections.unmodifiableList(actions);
    }

    public ModelObjectWithContextMenu.ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        return new ModelObjectWithContextMenu.ContextMenu().from(this, request, response);
    }

    @Restricted({NoExternalUse.class})
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK && !Jenkins.get().hasPermission(Jenkins.READ)) {
            return null;
        }
        return this;
    }

    @Restricted({NoExternalUse.class})
    static Set<String> getIllegalPersistedUsernames() {
        return new HashSet(Arrays.asList(ILLEGAL_PERSISTED_USERNAMES));
    }

    private Object writeReplace() {
        return XmlFile.replaceIfNotAtTopLevel(this, () -> {
            return new Replacer(this);
        });
    }

    /* loaded from: User$Replacer.class */
    private static class Replacer {
        private final String id;

        Replacer(User u) {
            this.id = u.getId();
        }

        private Object readResolve() {
            return User.getById(this.id, false);
        }
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: User$AllUsers.class */
    public static final class AllUsers {
        private boolean migratedUserIdMapper;
        private final ConcurrentMap<String, User> byName = new ConcurrentHashMap();

        synchronized void migrateUserIdMapper() {
            if (!this.migratedUserIdMapper) {
                try {
                    UserIdMapper.migrate();
                } catch (IOException x) {
                    User.LOGGER.log(Level.WARNING, (String) null, (Throwable) x);
                }
                this.migratedUserIdMapper = true;
            }
        }

        @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
        public static void scanAll() throws IOException {
            User.DIRNAMES.createMac();
            AllUsers instance = getInstance();
            instance.migrateUserIdMapper();
            File[] subdirectories = User.getUsersDirectory().listFiles();
            if (subdirectories == null) {
                return;
            }
            ConcurrentMap<String, User> byName = instance.byName;
            IdStrategy idStrategy = User.idStrategy();
            for (File dir : subdirectories) {
                String dirName = dir.getName();
                if (!User.HASHED_DIRNAMES.matcher(dirName).matches()) {
                    User.LOGGER.fine(() -> {
                        return "ignoring unrecognized dir " + String.valueOf(dir);
                    });
                } else {
                    XmlFile xml = new XmlFile(User.XSTREAM, new File(dir, User.CONFIG_XML));
                    if (!xml.exists()) {
                        User.LOGGER.fine(() -> {
                            return "ignoring dir " + String.valueOf(dir) + " with no config.xml";
                        });
                    } else {
                        User user = new User();
                        try {
                            xml.unmarshal(user);
                            if (user.id == null) {
                                User.LOGGER.warning(() -> {
                                    return "ignoring " + String.valueOf(xml) + " with no <id>";
                                });
                            } else {
                                String expectedFolderName = User.getUserFolderNameFor(user.id);
                                if (!dirName.equals(expectedFolderName)) {
                                    User.LOGGER.warning(() -> {
                                        return "ignoring " + String.valueOf(xml) + " with <id> " + user.id + " expected to be in " + expectedFolderName;
                                    });
                                } else {
                                    user.fixUpAfterLoad();
                                    User old = byName.put(idStrategy.keyFor(user.id), user);
                                    if (old != null) {
                                        User.LOGGER.warning(() -> {
                                            return "entry for " + user.id + " in " + String.valueOf(dir) + " duplicates one seen earlier for " + old.id;
                                        });
                                    } else {
                                        User.LOGGER.fine(() -> {
                                            return "successfully loaded " + user.id + " from " + String.valueOf(xml);
                                        });
                                    }
                                }
                            }
                        } catch (Exception x) {
                            User.LOGGER.log(Level.WARNING, "failed to load " + String.valueOf(xml), (Throwable) x);
                        }
                    }
                }
            }
            User.LOGGER.fine(() -> {
                return "loaded " + byName.size() + " entries";
            });
        }

        private static AllUsers getInstance() {
            return (AllUsers) ExtensionList.lookupSingleton(AllUsers.class);
        }

        private static void reload() throws IOException {
            getInstance().byName.clear();
            UserDetailsCache.get().invalidateAll();
            scanAll();
        }

        private static void clear() {
            getInstance().byName.clear();
        }

        private static void remove(String id) {
            getInstance().byName.remove(User.idStrategy().keyFor(id));
        }

        private static User get(String id) {
            return getInstance().byName.get(User.idStrategy().keyFor(id));
        }

        private static void put(String id, User user) {
            getInstance().byName.putIfAbsent(User.idStrategy().keyFor(id), user);
        }

        private static Collection<User> values() {
            return getInstance().byName.values();
        }
    }

    /* loaded from: User$CanonicalIdResolver.class */
    public static abstract class CanonicalIdResolver implements Describable<CanonicalIdResolver>, ExtensionPoint, Comparable<CanonicalIdResolver> {
        public static final String REALM = "realm";

        @CheckForNull
        public abstract String resolveCanonicalId(String idOrFullName, Map<String, ?> context);

        @Override // java.lang.Comparable
        public int compareTo(@NonNull CanonicalIdResolver o) {
            return Integer.compare(o.getPriority(), getPriority());
        }

        public int getPriority() {
            return 1;
        }

        public static List<CanonicalIdResolver> all() {
            List<CanonicalIdResolver> resolvers = new ArrayList<>((Collection<? extends CanonicalIdResolver>) ExtensionList.lookup(CanonicalIdResolver.class));
            Collections.sort(resolvers);
            return resolvers;
        }

        @CheckForNull
        public static String resolve(@NonNull String idOrFullName, @NonNull Map<String, ?> context) {
            for (CanonicalIdResolver resolver : all()) {
                String id = resolver.resolveCanonicalId(idOrFullName, context);
                if (id != null) {
                    User.LOGGER.log(Level.FINE, "{0} mapped {1} to {2}", new Object[]{resolver, idOrFullName, id});
                    return id;
                }
            }
            return null;
        }
    }

    @Extension
    @Symbol({"fullName"})
    /* loaded from: User$FullNameIdResolver.class */
    public static class FullNameIdResolver extends CanonicalIdResolver {
        @Override // hudson.model.User.CanonicalIdResolver
        public String resolveCanonicalId(String idOrFullName, Map<String, ?> context) {
            for (User user : User.getAll()) {
                if (idOrFullName.equals(user.getFullName())) {
                    return user.getId();
                }
            }
            return null;
        }

        @Override // hudson.model.User.CanonicalIdResolver
        public int getPriority() {
            return -1;
        }
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: User$UserIDCanonicalIdResolver.class */
    public static class UserIDCanonicalIdResolver extends CanonicalIdResolver {
        private static boolean SECURITY_243_FULL_DEFENSE = SystemProperties.getBoolean(User.class.getName() + ".SECURITY_243_FULL_DEFENSE", true);
        private static final ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> {
            return false;
        });

        @Override // hudson.model.User.CanonicalIdResolver
        public String resolveCanonicalId(String idOrFullName, Map<String, ?> context) {
            User existing = User.getById(idOrFullName, false);
            if (existing != null) {
                return existing.getId();
            }
            if (!SECURITY_243_FULL_DEFENSE || resolving.get().booleanValue()) {
                return null;
            }
            resolving.set(true);
            try {
                try {
                    UserDetails userDetails = UserDetailsCache.get().loadUserByUsername(idOrFullName);
                    String username = userDetails.getUsername();
                    resolving.set(false);
                    return username;
                } catch (ExecutionException x) {
                    User.LOGGER.log(Level.FINE, "could not look up " + idOrFullName, (Throwable) x);
                    resolving.set(false);
                    return null;
                } catch (UsernameNotFoundException e) {
                    User.LOGGER.log(Level.FINER, "not sure whether " + idOrFullName + " is a valid username or not", e);
                    resolving.set(false);
                    return null;
                }
            } catch (Throwable th) {
                resolving.set(false);
                throw th;
            }
        }

        @Override // hudson.model.User.CanonicalIdResolver
        public int getPriority() {
            return Integer.MAX_VALUE;
        }
    }
}
