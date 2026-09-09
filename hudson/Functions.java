package hudson;

import com.google.common.base.Predicate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.cli.CLICommand;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotatorFactory;
import hudson.init.InitMilestone;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.PageDecorator;
import hudson.model.PaneStatusProperties;
import hudson.model.ParameterDefinition;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.model.TimeZoneProperty;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.model.View;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.search.SearchFactory;
import hudson.search.SearchableModelObject;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.GlobalSecurityConfiguration;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.security.captcha.CaptchaSupport;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Publisher;
import hudson.tasks.UserAvatarResolver;
import hudson.util.Area;
import hudson.util.FormValidation;
import hudson.util.Iterators;
import hudson.util.Secret;
import hudson.util.jna.GNUCLibrary;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import hudson.widgets.RenderOnDemandClosure;
import io.jenkins.servlet.http.CookieWrapper;
import io.jenkins.servlet.http.HttpServletRequestWrapper;
import io.jenkins.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.Thread;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.console.ConsoleUrlProvider;
import jenkins.console.DefaultConsoleUrlProvider;
import jenkins.console.WithConsoleUrl;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.SimplePageDecorator;
import jenkins.model.details.Detail;
import jenkins.model.details.DetailFactory;
import jenkins.model.details.DetailGroup;
import jenkins.model.menu.Group;
import jenkins.telemetry.impl.PasswordMasking;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jexl.parser.ASTSizeFunction;
import org.apache.commons.jexl.util.Introspector;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.RawHtmlArgument;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/* loaded from: Functions.class */
public class Functions {
    private static final AtomicLong iota;
    private static Logger LOGGER;
    private static boolean NON_RECURSIVE_PASSWORD_MASKING_PERMISSION_CHECK;
    private static final Pattern ICON_SIZE;

    @SuppressFBWarnings(value = {"MS_SHOULD_BE_FINAL"}, justification = "for script console")
    @Deprecated(forRemoval = true, since = "TODO")
    public static boolean DEBUG_YUI;
    private static final SimpleFormatter formatter;
    private static String footerURL;
    private static final Pattern LINE_END;
    static final /* synthetic */ boolean $assertionsDisabled;

    static {
        $assertionsDisabled = !Functions.class.desiredAssertionStatus();
        iota = new AtomicLong();
        LOGGER = Logger.getLogger(Functions.class.getName());
        NON_RECURSIVE_PASSWORD_MASKING_PERMISSION_CHECK = SystemProperties.getBoolean(Functions.class.getName() + ".nonRecursivePasswordMaskingPermissionCheck");
        ICON_SIZE = Pattern.compile("\\d+x\\d+");
        formatter = new SimpleFormatter();
        footerURL = null;
        LINE_END = Pattern.compile("\r?\n");
    }

    public String generateId() {
        return "id" + iota.getAndIncrement();
    }

    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static boolean isModelWithContextMenu(Object o) {
        return o instanceof ModelObjectWithContextMenu;
    }

    public static boolean isModelWithChildren(Object o) {
        return o instanceof ModelObjectWithChildren;
    }

    @Deprecated
    public static boolean isMatrixProject(Object o) {
        return o != null && o.getClass().getName().equals("hudson.matrix.MatrixProject");
    }

    public static String xsDate(Calendar cal) {
        return Util.XS_DATETIME_FORMATTER2.format(cal.toInstant());
    }

    @Restricted({NoExternalUse.class})
    public static String iso8601DateTime(Date date) {
        return Util.XS_DATETIME_FORMATTER2.format(date.toInstant());
    }

    @Restricted({NoExternalUse.class})
    public static String localDate(Date date) {
        return DateFormat.getDateInstance(3).format(date);
    }

    public static String rfc822Date(Calendar cal) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(cal.toInstant(), ZoneId.systemDefault()));
    }

    @Restricted({NoExternalUse.class})
    public static String getTimeSpanString(Date date) {
        return Util.getTimeSpanString(Math.abs(date.getTime() - new Date().getTime()));
    }

    public static boolean isExtensionsAvailable() {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        return (jenkins2 == null || jenkins2.getInitLevel().compareTo(InitMilestone.EXTENSIONS_AUGMENTED) < 0 || jenkins2.isTerminating()) ? false : true;
    }

    public static void initPageVariables(JellyContext context) {
        StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
        currentRequest.getWebApp().getDispatchValidator().allowDispatch(currentRequest, Stapler.getCurrentResponse2());
        String rootURL = currentRequest.getContextPath();
        Functions h = new Functions();
        context.setVariable("h", h);
        context.setVariable("rootURL", rootURL);
        context.setVariable("resURL", rootURL + getResourcePath());
        context.setVariable("imagesURL", rootURL + getResourcePath() + "/images");
        context.setVariable("divBasedFormLayout", true);
        context.setVariable("userAgent", currentRequest.getHeader("User-Agent"));
        IconSet.initPageVariables(context);
    }

    public static <B> Class getTypeParameter(Class<? extends B> c, Class<B> base, int n) {
        Type parameterization = Types.getBaseClass(c, base);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            return Types.erasure(Types.getTypeArgument(pt, n));
        }
        throw new AssertionError(String.valueOf(c) + " doesn't properly parameterize " + String.valueOf(base));
    }

    public JDK.DescriptorImpl getJDKDescriptor() {
        return Jenkins.get().getDescriptorByType(JDK.DescriptorImpl.class);
    }

    public static String getDiffString(int i) {
        if (i == 0) {
            return "±0";
        }
        String s = Integer.toString(i);
        return i > 0 ? "+" + s : s;
    }

    public static String getDiffString2(int i) {
        if (i == 0) {
            return "";
        }
        String s = Integer.toString(i);
        return i > 0 ? "+" + s : s;
    }

    public static String getDiffString2(String prefix, int i, String suffix) {
        if (i == 0) {
            return "";
        }
        String s = Integer.toString(i);
        return i > 0 ? prefix + "+" + s + suffix : prefix + s + suffix;
    }

    public static String addSuffix(int n, String singular, String plural) {
        StringBuilder buf = new StringBuilder();
        buf.append(n).append(' ');
        if (n == 1) {
            buf.append(singular);
        } else {
            buf.append(plural);
        }
        return buf.toString();
    }

    public static RunUrl decompose(StaplerRequest2 req) {
        List<Ancestor> ancestors = req.getAncestors();
        Ancestor f = null;
        Ancestor l = null;
        for (Ancestor anc : ancestors) {
            if (anc.getObject() instanceof Run) {
                if (f == null) {
                    f = anc;
                }
                l = anc;
            }
        }
        if (l == null) {
            return null;
        }
        String head = f.getPrev().getUrl() + "/";
        String base = l.getUrl();
        String reqUri = req.getOriginalRequestURI();
        String furl = f.getUrl();
        int slashCount = 0;
        int indexOf = furl.indexOf(47);
        while (true) {
            int i = indexOf;
            if (i < 0) {
                String rest = reqUri.replaceFirst("(?:/+[^/]*){" + slashCount + "}", "");
                return new RunUrl((Run) f.getObject(), head, base, rest);
            }
            slashCount++;
            indexOf = furl.indexOf(47, i + 1);
        }
    }

    @Deprecated
    public static RunUrl decompose(StaplerRequest req) {
        return decompose(StaplerRequest.toStaplerRequest2(req));
    }

    public static Area getScreenResolution() {
        Cookie res = getCookie((HttpServletRequest) Stapler.getCurrentRequest2(), "screenResolution");
        if (res != null) {
            return Area.parse(res.getValue());
        }
        return null;
    }

    @Restricted({NoExternalUse.class})
    public static boolean useHidingPasswordFields() {
        return SystemProperties.getBoolean(Functions.class.getName() + ".hidingPasswordFields", true);
    }

    public static Node.Mode[] getNodeModes() {
        return Node.Mode.values();
    }

    public static String getProjectListString(List<AbstractProject> projects) {
        return Items.toNameList(projects);
    }

    @Deprecated
    public static Object ifThenElse(boolean cond, Object thenValue, Object elseValue) {
        return cond ? thenValue : elseValue;
    }

    public static String appendIfNotNull(String text, String suffix, String nullText) {
        return text == null ? nullText : text + suffix;
    }

    public static Map getSystemProperties() {
        return new TreeMap(System.getProperties());
    }

    @Restricted({DoNotUse.class})
    public static String getSystemProperty(String key) {
        return SystemProperties.getString(key);
    }

    public static Map getEnvVars() {
        return new TreeMap(EnvVars.masterEnvVars);
    }

    public static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }

    public static boolean isGlibcSupported() {
        try {
            GNUCLibrary.LIBC.getpid();
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    public static List<LogRecord> getLogRecords() {
        return Jenkins.logRecords;
    }

    public static String printLogRecord(LogRecord r) {
        return formatter.format(r);
    }

    @Restricted({NoExternalUse.class})
    public static String[] printLogRecordHtml(LogRecord r, LogRecord prior) {
        String[] oldParts = prior == null ? new String[4] : logRecordPreformat(prior);
        String[] newParts = logRecordPreformat(r);
        for (int i = 0; i < 3; i++) {
            newParts[i] = "<span class='" + (newParts[i].equals(oldParts[i]) ? "logrecord-metadata-old" : "logrecord-metadata-new") + "'>" + newParts[i] + "</span>";
        }
        newParts[3] = Util.xmlEscape(newParts[3]);
        return newParts;
    }

    private static String[] logRecordPreformat(LogRecord r) {
        String source;
        if (r.getSourceClassName() == null) {
            source = r.getLoggerName() == null ? "" : r.getLoggerName();
        } else if (r.getSourceMethodName() == null) {
            source = r.getSourceClassName();
        } else {
            source = r.getSourceClassName() + " " + r.getSourceMethodName();
        }
        String message = new SimpleFormatter().formatMessage(r) + "\n";
        Throwable x = r.getThrown();
        String[] strArr = new String[4];
        strArr[0] = String.format("%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp", new Date(r.getMillis()));
        strArr[1] = source;
        strArr[2] = r.getLevel().getLocalizedName();
        strArr[3] = x == null ? message : message + printThrowable(x) + "\n";
        return strArr;
    }

    public static <T> Iterable<T> reverse(Collection<T> collection) {
        List<T> list = new ArrayList<>((Collection<? extends T>) collection);
        Collections.reverse(list);
        return list;
    }

    public static Cookie getCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie;
                }
            }
            return null;
        }
        return null;
    }

    @Deprecated
    public static javax.servlet.http.Cookie getCookie(javax.servlet.http.HttpServletRequest req, String name) {
        return CookieWrapper.fromJakartaServletHttpCookie(getCookie(HttpServletRequestWrapper.toJakartaHttpServletRequest(req), name));
    }

    public static String getCookie(HttpServletRequest req, String name, String defaultValue) {
        Cookie c = getCookie(req, name);
        return (c == null || c.getValue() == null) ? defaultValue : c.getValue();
    }

    @Deprecated
    public static String getCookie(javax.servlet.http.HttpServletRequest req, String name, String defaultValue) {
        return getCookie(HttpServletRequestWrapper.toJakartaHttpServletRequest(req), name, defaultValue);
    }

    @Restricted({NoExternalUse.class})
    public static String validateIconSize(String iconSize) throws SecurityException {
        if (!ICON_SIZE.matcher(iconSize).matches()) {
            throw new SecurityException("invalid iconSize");
        }
        return iconSize;
    }

    public static <V> SortedMap<Integer, V> filter(SortedMap<Integer, V> map, String from, String to) {
        if (from == null && to == null) {
            return map;
        }
        if (to == null) {
            return map.headMap(Integer.valueOf(Integer.parseInt(from) - 1));
        }
        if (from == null) {
            return map.tailMap(Integer.valueOf(Integer.parseInt(to)));
        }
        return map.subMap(Integer.valueOf(Integer.parseInt(to)), Integer.valueOf(Integer.parseInt(from) - 1));
    }

    @Restricted({NoExternalUse.class})
    public static <V> SortedMap<Integer, V> filterExcludingFrom(SortedMap<Integer, V> map, String from, String to) {
        if (from == null && to == null) {
            return map;
        }
        if (to == null) {
            return map.headMap(Integer.valueOf(Integer.parseInt(from)));
        }
        if (from == null) {
            return map.tailMap(Integer.valueOf(Integer.parseInt(to)));
        }
        return map.subMap(Integer.valueOf(Integer.parseInt(to)), Integer.valueOf(Integer.parseInt(from)));
    }

    @Deprecated
    public static void configureAutoRefresh(HttpServletRequest request, HttpServletResponse response, boolean noAutoRefresh) {
    }

    @Deprecated
    public static boolean isAutoRefresh(HttpServletRequest request) {
        return false;
    }

    public static boolean isCollapsed(String paneId) {
        return PaneStatusProperties.forCurrentUser().isCollapsed(paneId);
    }

    @Restricted({NoExternalUse.class})
    public static boolean isUserTimeZoneOverride() {
        return TimeZoneProperty.forCurrentUser() != null;
    }

    @CheckForNull
    @Restricted({NoExternalUse.class})
    public static String getUserTimeZone() {
        return TimeZoneProperty.forCurrentUser();
    }

    @Restricted({NoExternalUse.class})
    public static String getUserTimeZonePostfix(Date date) {
        if (!isUserTimeZoneOverride()) {
            return "";
        }
        TimeZone tz = TimeZone.getTimeZone(getUserTimeZone());
        return tz.getDisplayName(tz.inDaylightTime(date), 0, getCurrentLocale());
    }

    @Restricted({NoExternalUse.class})
    public static long getHourLocalTimezone() {
        TimeZone tz = TimeZone.getDefault();
        return TimeUnit.MILLISECONDS.toHours(tz.getRawOffset() + tz.getDSTSavings());
    }

    public static String getNearestAncestorUrl(StaplerRequest2 req, Object it) {
        List list = req.getAncestors();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ancestor anc = (Ancestor) list.get(i);
            if (anc.getObject() == it) {
                return anc.getUrl();
            }
        }
        return null;
    }

    @Deprecated
    public static String getNearestAncestorUrl(StaplerRequest req, Object it) {
        return getNearestAncestorUrl(StaplerRequest.toStaplerRequest2(req), it);
    }

    public static String getSearchURL() {
        List list = Stapler.getCurrentRequest2().getAncestors();
        for (int i = list.size() - 1; i >= 0; i--) {
            Ancestor anc = (Ancestor) list.get(i);
            if (anc.getObject() instanceof SearchableModelObject) {
                return anc.getUrl() + "/search/";
            }
        }
        return null;
    }

    public static String appendSpaceIfNotNull(String n) {
        if (n == null) {
            return null;
        }
        return n + " ";
    }

    public static String nbspIndent(String size) {
        int i = size.indexOf(120);
        return "&nbsp;".repeat(Math.max(0, (Integer.parseInt(i > 0 ? size.substring(0, i) : size) / 10) - 1));
    }

    public static String getWin32ErrorMessage(IOException e) {
        return Util.getWin32ErrorMessage(e);
    }

    public static boolean isMultiline(String s) {
        if (s == null) {
            return false;
        }
        return s.indexOf(13) >= 0 || s.indexOf(10) >= 0;
    }

    public static String encode(String s) {
        return Util.encode(s);
    }

    public static String urlEncode(String s) {
        if (s == null) {
            return "";
        }
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String escape(String s) {
        return Util.escape(s);
    }

    public static String xmlEscape(String s) {
        return Util.xmlEscape(s);
    }

    public static String xmlUnescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    public static String htmlAttributeEscape(String text) {
        StringBuilder buf = new StringBuilder(text.length() + 64);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                buf.append("&lt;");
            } else if (ch == '>') {
                buf.append("&gt;");
            } else if (ch == '&') {
                buf.append("&amp;");
            } else if (ch == '\"') {
                buf.append("&quot;");
            } else if (ch == '\'') {
                buf.append("&#39;");
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    public static void checkPermission(Permission permission) {
        checkPermission((AccessControlled) Jenkins.get(), permission);
    }

    public static void checkPermission(AccessControlled object, Permission permission) {
        if (permission != null) {
            object.checkPermission(permission);
        }
    }

    public static void checkPermission(Object object, Permission permission) {
        if (permission == null) {
            return;
        }
        if (object instanceof AccessControlled) {
            checkPermission((AccessControlled) object, permission);
            return;
        }
        List<Ancestor> ancs = Stapler.getCurrentRequest2().getAncestors();
        for (Ancestor anc : Iterators.reverse(ancs)) {
            Object o = anc.getObject();
            if (o instanceof AccessControlled) {
                checkPermission((AccessControlled) o, permission);
                return;
            }
        }
        checkPermission((AccessControlled) Jenkins.get(), permission);
    }

    public static boolean hasPermission(Permission permission) {
        return hasPermission(Jenkins.get(), permission);
    }

    public static boolean hasPermission(Object object, Permission permission) {
        if (permission == null) {
            return true;
        }
        if (object instanceof AccessControlled) {
            return ((AccessControlled) object).hasPermission(permission);
        }
        List<Ancestor> ancs = Stapler.getCurrentRequest2().getAncestors();
        for (Ancestor anc : Iterators.reverse(ancs)) {
            Object o = anc.getObject();
            if (o instanceof AccessControlled) {
                return ((AccessControlled) o).hasPermission(permission);
            }
        }
        return Jenkins.get().hasPermission(permission);
    }

    public static String inferHudsonURL(StaplerRequest2 req) {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl != null) {
            return rootUrl;
        }
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()).append("://");
        buf.append(req.getServerName());
        if ((!req.getScheme().equals("http") || req.getLocalPort() != 80) && (!req.getScheme().equals("https") || req.getLocalPort() != 443)) {
            buf.append(':').append(req.getLocalPort());
        }
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    @Deprecated
    public static String inferHudsonURL(StaplerRequest req) {
        return inferHudsonURL(StaplerRequest.toStaplerRequest2(req));
    }

    public static String getFooterURL() {
        if (footerURL == null) {
            footerURL = SystemProperties.getString("hudson.footerURL");
            if (footerURL == null || footerURL.isBlank()) {
                footerURL = "https://www.jenkins.io/";
            }
        }
        return footerURL;
    }

    public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Class<? extends Job> clazz) {
        return JobPropertyDescriptor.getPropertyDescriptors(clazz);
    }

    public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Job job) {
        return DescriptorVisibilityFilter.apply(job, JobPropertyDescriptor.getPropertyDescriptors(job.getClass()));
    }

    public static List<Descriptor<BuildWrapper>> getBuildWrapperDescriptors(AbstractProject<?, ?> project) {
        return BuildWrappers.getFor(project);
    }

    public static List<Descriptor<SecurityRealm>> getSecurityRealmDescriptors() {
        return SecurityRealm.all();
    }

    public static List<Descriptor<AuthorizationStrategy>> getAuthorizationStrategyDescriptors() {
        return AuthorizationStrategy.all();
    }

    public static List<Descriptor<Builder>> getBuilderDescriptors(AbstractProject<?, ?> project) {
        return BuildStepDescriptor.filter(Builder.all(), project.getClass());
    }

    public static List<Descriptor<Publisher>> getPublisherDescriptors(AbstractProject<?, ?> project) {
        return BuildStepDescriptor.filter(Publisher.all(), project.getClass());
    }

    public static List<SCMDescriptor<?>> getSCMDescriptors(AbstractProject<?, ?> project) {
        return SCM._for(project);
    }

    @Restricted({DoNotUse.class})
    @RestrictedSince("2.12")
    @Deprecated
    public static List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
        return Jenkins.get().getDescriptorList(ComputerLauncher.class);
    }

    @Restricted({DoNotUse.class})
    @RestrictedSince("2.12")
    @Deprecated
    public static List<Descriptor<RetentionStrategy<?>>> getRetentionStrategyDescriptors() {
        return RetentionStrategy.all();
    }

    public static List<ParameterDefinition.ParameterDescriptor> getParameterDescriptors() {
        return ParameterDefinition.all();
    }

    public static List<Descriptor<CaptchaSupport>> getCaptchaSupportDescriptors() {
        return CaptchaSupport.all();
    }

    public static List<Descriptor<ViewsTabBar>> getViewsTabBarDescriptors() {
        return ViewsTabBar.all();
    }

    public static List<Descriptor<MyViewsTabBar>> getMyViewsTabBarDescriptors() {
        return MyViewsTabBar.all();
    }

    @Restricted({DoNotUse.class})
    @RestrictedSince("2.12")
    @Deprecated
    public static List<NodePropertyDescriptor> getNodePropertyDescriptors(Class<? extends Node> clazz) {
        List<NodePropertyDescriptor> result = new ArrayList<>();
        for (NodePropertyDescriptor npd : Jenkins.get().getDescriptorList(NodeProperty.class)) {
            if (npd.isApplicable(clazz)) {
                result.add(npd);
            }
        }
        return result;
    }

    public static List<NodePropertyDescriptor> getGlobalNodePropertyDescriptors() {
        List<NodePropertyDescriptor> result = new ArrayList<>();
        for (NodePropertyDescriptor npd : Jenkins.get().getDescriptorList(NodeProperty.class)) {
            if (npd.isApplicableAsGlobal()) {
                result.add(npd);
            }
        }
        return result;
    }

    @Restricted({NoExternalUse.class})
    @Deprecated
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfig(Predicate<GlobalConfigurationCategory> predicate) {
        ExtensionList<Descriptor> exts = ExtensionList.lookup(Descriptor.class);
        List<Tag> r = new ArrayList<>(exts.size());
        for (ExtensionComponent<Descriptor> c : exts.getComponents()) {
            Descriptor d = (Descriptor) c.getInstance();
            if (d.getGlobalConfigPage() != null && Jenkins.get().hasPermission(d.getRequiredGlobalConfigPagePermission()) && predicate.apply(d.getCategory())) {
                r.add(new Tag(c.ordinal(), d));
            }
        }
        Collections.sort(r);
        List<Descriptor> answer = new ArrayList<>(r.size());
        Iterator<Tag> it = r.iterator();
        while (it.hasNext()) {
            answer.add(it.next().d);
        }
        return DescriptorVisibilityFilter.apply(Jenkins.get(), answer);
    }

    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigByDescriptor(java.util.function.Predicate<Descriptor> predicate) {
        ExtensionList<Descriptor> exts = ExtensionList.lookup(Descriptor.class);
        List<Tag> r = new ArrayList<>(exts.size());
        for (ExtensionComponent<Descriptor> c : exts.getComponents()) {
            Descriptor d = (Descriptor) c.getInstance();
            if (d.getGlobalConfigPage() != null && predicate.test(d)) {
                r.add(new Tag(c.ordinal(), d));
            }
        }
        Collections.sort(r);
        List<Descriptor> answer = new ArrayList<>(r.size());
        Iterator<Tag> it = r.iterator();
        while (it.hasNext()) {
            answer.add(it.next().d);
        }
        return DescriptorVisibilityFilter.apply(Jenkins.get(), answer);
    }

    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigByDescriptor() {
        return getSortedDescriptorsForGlobalConfigByDescriptor(descriptor -> {
            return true;
        });
    }

    @Deprecated
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigNoSecurity() {
        return getSortedDescriptorsForGlobalConfigByDescriptor(d -> {
            return GlobalSecurityConfiguration.FILTER.negate().test(d);
        });
    }

    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigUnclassified() {
        return getSortedDescriptorsForGlobalConfigByDescriptor(d -> {
            return (d.getCategory() instanceof GlobalConfigurationCategory.Unclassified) && Jenkins.get().hasPermission(d.getRequiredGlobalConfigPagePermission());
        });
    }

    @Restricted({NoExternalUse.class})
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigUnclassifiedReadable() {
        return getSortedDescriptorsForGlobalConfigByDescriptor(d -> {
            return (d.getCategory() instanceof GlobalConfigurationCategory.Unclassified) && (Jenkins.get().hasPermission(d.getRequiredGlobalConfigPagePermission()) || Jenkins.get().hasPermission(Jenkins.SYSTEM_READ));
        });
    }

    public static boolean hasAnyPermission(AccessControlled ac, Permission[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        return ac.hasAnyPermission(permissions);
    }

    public static boolean hasAnyPermission(Object object, Permission[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        if (object instanceof AccessControlled) {
            return hasAnyPermission((AccessControlled) object, permissions);
        }
        AccessControlled ac = (AccessControlled) Stapler.getCurrentRequest2().findAncestorObject(AccessControlled.class);
        return hasAnyPermission((AccessControlled) Objects.requireNonNullElseGet(ac, Jenkins::get), permissions);
    }

    public static void checkAnyPermission(AccessControlled ac, Permission[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return;
        }
        ac.checkAnyPermission(permissions);
    }

    public static void checkAnyPermission(Object object, Permission[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return;
        }
        if (object instanceof AccessControlled) {
            checkAnyPermission((AccessControlled) object, permissions);
            return;
        }
        List<Ancestor> ancs = Stapler.getCurrentRequest2().getAncestors();
        for (Ancestor anc : Iterators.reverse(ancs)) {
            Object o = anc.getObject();
            if (o instanceof AccessControlled) {
                checkAnyPermission((AccessControlled) o, permissions);
                return;
            }
        }
        checkAnyPermission((AccessControlled) Jenkins.get(), permissions);
    }

    /* loaded from: Functions$Tag.class */
    private static class Tag implements Comparable<Tag> {
        double ordinal;
        String hierarchy;
        Descriptor d;

        Tag(double ordinal, Descriptor d) {
            this.ordinal = ordinal;
            this.d = d;
            this.hierarchy = buildSuperclassHierarchy(d.clazz, new StringBuilder()).toString();
        }

        private StringBuilder buildSuperclassHierarchy(Class c, StringBuilder buf) {
            Class sc = c.getSuperclass();
            if (sc != null) {
                buildSuperclassHierarchy(sc, buf).append(':');
            }
            return buf.append(c.getName());
        }

        @Override // java.lang.Comparable
        public int compareTo(Tag that) {
            int r = Double.compare(that.ordinal, this.ordinal);
            return r != 0 ? r : this.hierarchy.compareTo(that.hierarchy);
        }
    }

    public static String getIconFilePath(Action a) {
        String name = a.getIconFileName();
        if (name == null) {
            return null;
        }
        if (name.startsWith("symbol-")) {
            return name;
        }
        if (name.startsWith("/")) {
            return name.substring(1);
        }
        return "images/24x24/" + name;
    }

    public static int size2(Object o) throws Exception {
        if (o == null) {
            return 0;
        }
        return ASTSizeFunction.sizeOf(o, Introspector.getUberspect());
    }

    public static String getRelativeLinkTo(Item p) {
        Map<Object, String> ancestors = new HashMap<>();
        View view = null;
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        for (Ancestor a : request.getAncestors()) {
            ancestors.put(a.getObject(), a.getRelativePath());
            if (a.getObject() instanceof View) {
                view = (View) a.getObject();
            }
        }
        String path = ancestors.get(p);
        if (path != null) {
            return normalizeURI(path + "/");
        }
        Item i = p;
        String url = "";
        while (true) {
            ItemGroup ig = i.getParent();
            url = i.getShortUrl() + url;
            if (ig == Jenkins.get() || (view != null && ig == view.getOwner().getItemGroup())) {
                break;
            }
            String path2 = ancestors.get(ig);
            if (path2 != null) {
                return normalizeURI(path2 + "/" + url);
            }
            if (!$assertionsDisabled && !(ig instanceof Item)) {
                throw new AssertionError();
            }
            i = (Item) ig;
        }
        if (!$assertionsDisabled && !(i instanceof TopLevelItem)) {
            throw new AssertionError();
        }
        if (view != null) {
            return normalizeURI(ancestors.get(view) + "/" + url);
        }
        return normalizeURI(request.getContextPath() + "/" + p.getUrl());
    }

    private static String normalizeURI(String uri) {
        return URI.create(uri).normalize().toString();
    }

    public static List<TopLevelItem> getAllTopLevelItems(ItemGroup root) {
        return root.getAllItems(TopLevelItem.class);
    }

    @Nullable
    public static String getRelativeNameFrom(@CheckForNull Item p, @CheckForNull ItemGroup g, boolean useDisplayName) {
        if (p == null) {
            return null;
        }
        if (g == null) {
            return useDisplayName ? p.getFullDisplayName() : p.getFullName();
        }
        String separationString = useDisplayName ? " » " : "/";
        Map<ItemGroup, Integer> parents = new HashMap<>();
        int depth = 0;
        while (g != null) {
            int i = depth;
            depth++;
            parents.put(g, Integer.valueOf(i));
            if (g instanceof Item) {
                g = ((Item) g).getParent();
            } else {
                g = null;
            }
        }
        StringBuilder buf = new StringBuilder();
        Item item = p;
        while (true) {
            Item i2 = item;
            if (!buf.isEmpty()) {
                buf.insert(0, separationString);
            }
            buf.insert(0, useDisplayName ? i2.getDisplayName() : i2.getName());
            ItemGroup gr = i2.getParent();
            Integer d = parents.get(gr);
            if (d != null) {
                for (int j = d.intValue(); j > 0; j--) {
                    buf.insert(0, separationString);
                    buf.insert(0, "..");
                }
                return buf.toString();
            }
            if (gr instanceof Item) {
                item = (Item) gr;
            } else {
                return null;
            }
        }
    }

    @Nullable
    public static String getRelativeNameFrom(@CheckForNull Item p, @CheckForNull ItemGroup g) {
        return getRelativeNameFrom(p, g, false);
    }

    @Nullable
    public static String getRelativeDisplayNameFrom(@CheckForNull Item p, @CheckForNull ItemGroup g) {
        return getRelativeNameFrom(p, g, true);
    }

    public static Map<Thread, StackTraceElement[]> dumpAllThreads() {
        Map<Thread, StackTraceElement[]> sorted = new TreeMap<>(new ThreadSorter());
        sorted.putAll(Thread.getAllStackTraces());
        return sorted;
    }

    public static ThreadInfo[] getThreadInfos() {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        return mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(), mbean.isSynchronizerUsageSupported());
    }

    public static ThreadGroupMap sortThreadsAndGetGroupMap(ThreadInfo[] list) {
        ThreadGroupMap sorter = new ThreadGroupMap();
        Arrays.sort(list, sorter);
        return sorter;
    }

    /* loaded from: Functions$ThreadSorterBase.class */
    private static class ThreadSorterBase {
        protected Map<Long, String> map = new HashMap();

        ThreadSorterBase() {
            ThreadGroup tg;
            ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
            while (true) {
                tg = threadGroup;
                if (tg.getParent() == null) {
                    break;
                } else {
                    threadGroup = tg.getParent();
                }
            }
            Thread[] threads = new Thread[tg.activeCount() * 2];
            int threadsLen = tg.enumerate(threads, true);
            for (int i = 0; i < threadsLen; i++) {
                ThreadGroup group = threads[i].getThreadGroup();
                this.map.put(Long.valueOf(threads[i].getId()), group != null ? group.getName() : null);
            }
        }

        protected int compare(long idA, long idB) {
            String tga = this.map.get(Long.valueOf(idA));
            String tgb = this.map.get(Long.valueOf(idB));
            int result = (tga != null ? -1 : 0) + (tgb != null ? 1 : 0);
            if (result == 0 && tga != null) {
                result = tga.compareToIgnoreCase(tgb);
            }
            return result;
        }
    }

    /* loaded from: Functions$ThreadGroupMap.class */
    public static class ThreadGroupMap extends ThreadSorterBase implements Comparator<ThreadInfo>, Serializable {
        private static final long serialVersionUID = 7803975728695308444L;

        public String getThreadGroup(ThreadInfo ti) {
            return this.map.get(Long.valueOf(ti.getThreadId()));
        }

        @Override // java.util.Comparator
        public int compare(ThreadInfo a, ThreadInfo b) {
            int result = compare(a.getThreadId(), b.getThreadId());
            if (result == 0) {
                result = a.getThreadName().compareToIgnoreCase(b.getThreadName());
            }
            return result;
        }
    }

    /* loaded from: Functions$ThreadSorter.class */
    private static class ThreadSorter extends ThreadSorterBase implements Comparator<Thread>, Serializable {
        private static final long serialVersionUID = 5053631350439192685L;

        private ThreadSorter() {
        }

        @Override // java.util.Comparator
        public int compare(Thread a, Thread b) {
            int result = compare(a.getId(), b.getId());
            if (result == 0) {
                result = a.getName().compareToIgnoreCase(b.getName());
            }
            return result;
        }
    }

    @Deprecated
    public static boolean isMustangOrAbove() {
        return true;
    }

    public static String dumpThreadInfo(ThreadInfo ti, ThreadGroupMap map) {
        String grp = map.getThreadGroup(ti);
        String threadName = ti.getThreadName();
        long threadId = ti.getThreadId();
        String str = grp != null ? grp : Jenkins.UNCOMPUTED_VERSION;
        String.valueOf(ti.getThreadState());
        StringBuilder sb = new StringBuilder("\"" + threadName + "\" Id=" + threadId + " Group=" + sb + " " + str);
        if (ti.getLockName() != null) {
            sb.append(" on " + ti.getLockName());
        }
        if (ti.getLockOwnerName() != null) {
            sb.append(" owned by \"" + ti.getLockOwnerName() + "\" Id=" + ti.getLockOwnerId());
        }
        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        StackTraceElement[] stackTrace = ti.getStackTrace();
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat ").append(ste);
            sb.append('\n');
            if (i == 0 && ti.getLockInfo() != null) {
                Thread.State ts = ti.getThreadState();
                switch (AnonymousClass1.$SwitchMap$java$lang$Thread$State[ts.ordinal()]) {
                    case Maven.MavenInstallation.MAVEN_21 /* 1 */:
                        sb.append("\t-  blocked on ").append(ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case Maven.MavenInstallation.MAVEN_30 /* 2 */:
                    case 3:
                        sb.append("\t-  waiting on ").append(ti.getLockInfo());
                        sb.append('\n');
                        break;
                }
            }
            for (MonitorInfo mi : ti.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked ").append(mi);
                    sb.append('\n');
                }
            }
        }
        LockInfo[] locks = ti.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- ").append(li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /* renamed from: hudson.Functions$1, reason: invalid class name */
    /* loaded from: Functions$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$java$lang$Thread$State = new int[Thread.State.values().length];

        static {
            try {
                $SwitchMap$java$lang$Thread$State[Thread.State.BLOCKED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$java$lang$Thread$State[Thread.State.WAITING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$java$lang$Thread$State[Thread.State.TIMED_WAITING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    public static <T> Collection<T> emptyList() {
        return Collections.emptyList();
    }

    public static String jsStringEscape(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\"':
                    buf.append("\\\"");
                    break;
                case '\'':
                    buf.append("\\'");
                    break;
                case '\\':
                    buf.append("\\\\");
                    break;
                default:
                    buf.append(ch);
                    break;
            }
        }
        return buf.toString();
    }

    public static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String getVersion() {
        return Jenkins.VERSION;
    }

    public static String getResourcePath() {
        return Jenkins.RESOURCE_PATH;
    }

    public static String getViewResource(Object it, String path) {
        Class clazz = it.getClass();
        if (it instanceof Class) {
            clazz = (Class) it;
        }
        if (it instanceof Descriptor) {
            clazz = ((Descriptor) it).clazz;
        }
        String buf = Stapler.getCurrentRequest2().getContextPath() + Jenkins.VIEW_RESOURCE_PATH + "/" + clazz.getName().replace('.', '/').replace('$', '/') + "/" + path;
        return buf;
    }

    public static boolean hasView(Object it, String path) throws IOException {
        return (it == null || Stapler.getCurrentRequest2().getView(it, path) == null) ? false : true;
    }

    public static boolean defaultToTrue(Boolean b) {
        if (b == null) {
            return true;
        }
        return b.booleanValue();
    }

    public static <T> T defaulted(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    @NonNull
    public static String printThrowable(@CheckForNull Throwable t) {
        if (t == null) {
            return Messages.Functions_NoExceptionDetails();
        }
        StringBuilder s = new StringBuilder();
        doPrintStackTrace(s, t, null, "", new HashSet());
        return s.toString();
    }

    @SuppressFBWarnings(value = {"INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE"}, justification = "Jenkins handles this issue differently or doesn't care about it")
    private static void doPrintStackTrace(@NonNull StringBuilder s, @NonNull Throwable t, @CheckForNull Throwable higher, @NonNull String prefix, @NonNull Set<Throwable> encountered) {
        int higherEnd;
        if (!encountered.add(t)) {
            s.append("<cycle to ").append(t).append(">\n");
            return;
        }
        if (Util.isOverridden(Throwable.class, t.getClass(), "printStackTrace", new Class[]{PrintWriter.class})) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            s.append(sw);
            return;
        }
        Throwable lower = t.getCause();
        if (lower != null) {
            doPrintStackTrace(s, lower, t, prefix, encountered);
        }
        for (Throwable suppressed : t.getSuppressed()) {
            s.append(prefix).append("Also:   ");
            doPrintStackTrace(s, suppressed, t, prefix + "\t", encountered);
        }
        if (lower != null) {
            s.append(prefix).append("Caused: ");
        }
        String summary = t.toString();
        if (lower != null) {
            String suffix = ": " + String.valueOf(lower);
            if (summary.endsWith(suffix)) {
                summary = summary.substring(0, summary.length() - suffix.length());
            }
        }
        s.append(summary).append(System.lineSeparator());
        StackTraceElement[] trace = t.getStackTrace();
        int end = trace.length;
        if (higher != null) {
            StackTraceElement[] higherTrace = higher.getStackTrace();
            while (end > 0 && (higherEnd = (end + higherTrace.length) - trace.length) > 0 && higherTrace[higherEnd - 1].equals(trace[end - 1])) {
                end--;
            }
        }
        for (int i = 0; i < end; i++) {
            s.append(prefix).append("\tat ").append(trace[i]).append(System.lineSeparator());
        }
    }

    @SuppressFBWarnings(value = {"XSS_SERVLET"}, justification = "TODO needs triage")
    public static void printStackTrace(@CheckForNull Throwable t, @NonNull PrintWriter pw) {
        pw.println(printThrowable(t).trim());
    }

    public static void printStackTrace(@CheckForNull Throwable t, @NonNull PrintStream ps) {
        ps.println(printThrowable(t).trim());
    }

    public static int determineRows(String s) {
        if (s == null) {
            return 5;
        }
        return Math.max(5, LINE_END.split(s).length);
    }

    @Restricted({DoNotUse.class})
    @RestrictedSince("2.173")
    @Deprecated
    public static String toCCStatus(Item i) {
        return "Unknown";
    }

    public static boolean isAnonymous() {
        return ACL.isAnonymous2(Jenkins.getAuthentication2());
    }

    public static JellyContext getCurrentJellyContext() {
        JellyContext context = (JellyContext) ExpressionFactory2.CURRENT_CONTEXT.get();
        if ($assertionsDisabled || context != null) {
            return context;
        }
        throw new AssertionError();
    }

    public static String runScript(Script script) throws JellyTagException {
        StringWriter out = new StringWriter();
        script.run(getCurrentJellyContext(), XMLOutput.createXMLOutput(out));
        return out.toString();
    }

    public static <T> List<T> subList(List<T> base, int maxSize) {
        if (maxSize < base.size()) {
            return base.subList(0, maxSize);
        }
        return base;
    }

    public static String joinPath(String... components) {
        StringBuilder buf = new StringBuilder();
        for (String s : components) {
            if (!s.isEmpty()) {
                if (!buf.isEmpty()) {
                    if (buf.charAt(buf.length() - 1) != '/') {
                        buf.append('/');
                    }
                    if (s.charAt(0) == '/') {
                        s = s.substring(1);
                    }
                }
                buf.append(s);
            }
        }
        return buf.toString();
    }

    @CheckForNull
    public static String getActionUrl(String itUrl, Action action) {
        String urlName = action.getUrlName();
        if (urlName == null) {
            return null;
        }
        try {
            if (new URI(urlName).isAbsolute()) {
                return urlName;
            }
            return urlName.startsWith("/") ? joinPath(Stapler.getCurrentRequest2().getContextPath(), urlName) : joinPath(Stapler.getCurrentRequest2().getContextPath() + "/" + itUrl, urlName);
        } catch (URISyntaxException x) {
            Logger.getLogger(Functions.class.getName()).log(Level.WARNING, "Failed to parse URL for {0}: {1}", new Object[]{action, x});
            return null;
        }
    }

    @CheckForNull
    public static String getConsoleUrl(WithConsoleUrl withConsoleUrl) {
        String consoleUrl = withConsoleUrl.getConsoleUrl();
        if (consoleUrl != null) {
            return Stapler.getCurrentRequest2().getContextPath() + "/" + consoleUrl;
        }
        return null;
    }

    public static ConsoleUrlProvider getConsoleProviderFor(Run<?, ?> run) {
        return (ConsoleUrlProvider) Optional.ofNullable(ConsoleUrlProvider.getProvider(run)).orElse(new DefaultConsoleUrlProvider());
    }

    public static String toEmailSafeString(String projectName) {
        StringBuilder buf = new StringBuilder(projectName.length());
        for (int i = 0; i < projectName.length(); i++) {
            char ch = projectName.charAt(i);
            if (('a' <= ch && ch <= 'z') || (('A' <= ch && ch <= 'Z') || (('0' <= ch && ch <= '9') || "-_.".indexOf(ch) >= 0))) {
                buf.append(ch);
            } else {
                buf.append('_');
            }
        }
        return buf.toString();
    }

    @Deprecated
    public String getServerName() {
        String url = Jenkins.get().getRootUrl();
        if (url != null) {
            try {
                String host = new URL(url).getHost();
                if (host != null) {
                    return host;
                }
            } catch (MalformedURLException e) {
            }
        }
        return Stapler.getCurrentRequest2().getServerName();
    }

    @Deprecated
    public String getCheckUrl(String userDefined, Object descriptor, String field) {
        if (userDefined != null || field == null) {
            return userDefined;
        }
        if (descriptor instanceof Descriptor) {
            Descriptor d = (Descriptor) descriptor;
            return d.getCheckUrl(field);
        }
        return null;
    }

    public void calcCheckUrl(Map attributes, String userDefined, Object descriptor, String field) {
        if (userDefined == null && field != null && (descriptor instanceof Descriptor)) {
            Descriptor d = (Descriptor) descriptor;
            FormValidation.CheckMethod m = d.getCheckMethod(field);
            attributes.put("checkUrl", m.toStemUrl());
            attributes.put("checkDependsOn", m.getDependsOn());
        }
    }

    public boolean hyperlinkMatchesCurrentPage(String href) {
        String url = Stapler.getCurrentRequest2().getRequestURL().toString();
        if (href == null || href.length() <= 1) {
            return ".".equals(href) && url.endsWith("/");
        }
        String url2 = URLDecoder.decode(url, StandardCharsets.UTF_8);
        String href2 = URLDecoder.decode(href, StandardCharsets.UTF_8);
        if (url2.endsWith("/")) {
            url2 = url2.substring(0, url2.length() - 1);
        }
        if (href2.endsWith("/")) {
            href2 = href2.substring(0, href2.length() - 1);
        }
        return url2.endsWith(href2);
    }

    @Restricted({NoExternalUse.class})
    public static boolean showInPrimaryHeader(Action action) {
        if (action instanceof RootAction) {
            RootAction ra = (RootAction) action;
            if (ra.isPrimaryAction()) {
                return true;
            }
        }
        String path = Stapler.getCurrentRequest2().getPathInfo();
        if (path == null || path.equals("/")) {
            return false;
        }
        String actionPath = action.getUrlName();
        if (actionPath == null) {
            return false;
        }
        if (!actionPath.startsWith("/")) {
            actionPath = "/" + actionPath;
        }
        if (!actionPath.endsWith("/")) {
            actionPath = actionPath + "/";
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path.startsWith(actionPath);
    }

    @Deprecated
    public <T> List<T> singletonList(T t) {
        return List.of(t);
    }

    public static List<PageDecorator> getPageDecorators() {
        return Jenkins.getInstanceOrNull() == null ? Collections.emptyList() : PageDecorator.all();
    }

    public static SimplePageDecorator getSimplePageDecorator() {
        return SimplePageDecorator.first();
    }

    public static List<SimplePageDecorator> getSimplePageDecorators() {
        return SimplePageDecorator.all();
    }

    public static List<Descriptor<Cloud>> getCloudDescriptors() {
        return Cloud.all();
    }

    public String prepend(String prefix, String body) {
        if (body != null && !body.isEmpty()) {
            return prefix + body;
        }
        return body;
    }

    public static List<Descriptor<CrumbIssuer>> getCrumbIssuerDescriptors() {
        return CrumbIssuer.all();
    }

    public static String getCrumb(StaplerRequest2 req) {
        Jenkins h = Jenkins.getInstanceOrNull();
        CrumbIssuer issuer = h != null ? h.getCrumbIssuer() : null;
        return issuer != null ? issuer.getCrumb((ServletRequest) req) : "";
    }

    @Deprecated
    public static String getCrumb(StaplerRequest req) {
        return getCrumb(req != null ? StaplerRequest.toStaplerRequest2(req) : null);
    }

    public static String getCrumbRequestField() {
        Jenkins h = Jenkins.getInstanceOrNull();
        CrumbIssuer issuer = h != null ? h.getCrumbIssuer() : null;
        return issuer != null ? issuer.m74getDescriptor().getCrumbRequestField() : "";
    }

    public static Date getCurrentTime() {
        return new Date();
    }

    public static Locale getCurrentLocale() {
        Locale locale = null;
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            locale = req.getLocale();
        }
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return locale;
    }

    public static String generateConsoleAnnotationScriptAndStylesheet() {
        String cp = Stapler.getCurrentRequest2().getContextPath() + Jenkins.RESOURCE_PATH;
        StringBuilder buf = new StringBuilder();
        Iterator it = ConsoleAnnotatorFactory.all().iterator();
        while (it.hasNext()) {
            ConsoleAnnotatorFactory f = (ConsoleAnnotatorFactory) it.next();
            String path = cp + "/" + ConsoleAnnotatorFactory.class.getName() + "/" + f.getClass().getName();
            if (f.hasScript()) {
                buf.append("<script src='").append(path).append("/script.js'></script>");
            }
            if (f.hasStylesheet()) {
                buf.append("<link rel='stylesheet' type='text/css' href='").append(path).append("/style.css' />");
            }
        }
        Iterator it2 = ConsoleAnnotationDescriptor.all().iterator();
        while (it2.hasNext()) {
            ConsoleAnnotationDescriptor d = (ConsoleAnnotationDescriptor) it2.next();
            String path2 = cp + "/descriptor/" + d.clazz.getName();
            if (d.hasScript()) {
                buf.append("<script src='").append(path2).append("/script.js'></script>");
            }
            if (d.hasStylesheet()) {
                buf.append("<link rel='stylesheet' type='text/css' href='").append(path2).append("/style.css' />");
            }
        }
        return buf.toString();
    }

    /* JADX WARN: Code restructure failed: missing block: B:10:?, code lost:
    
        continue;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public java.util.List<java.lang.String> getLoggerNames() {
        /*
            r3 = this;
        L0:
            java.util.ArrayList r0 = new java.util.ArrayList     // Catch: java.util.ConcurrentModificationException -> L2d
            r1 = r0
            r1.<init>()     // Catch: java.util.ConcurrentModificationException -> L2d
            r4 = r0
            java.util.logging.LogManager r0 = java.util.logging.LogManager.getLogManager()     // Catch: java.util.ConcurrentModificationException -> L2d
            java.util.Enumeration r0 = r0.getLoggerNames()     // Catch: java.util.ConcurrentModificationException -> L2d
            r5 = r0
        Lf:
            r0 = r5
            boolean r0 = r0.hasMoreElements()     // Catch: java.util.ConcurrentModificationException -> L2d
            if (r0 == 0) goto L2b
            r0 = r4
            r1 = r5
            java.lang.Object r1 = r1.nextElement()     // Catch: java.util.ConcurrentModificationException -> L2d
            java.lang.String r1 = (java.lang.String) r1     // Catch: java.util.ConcurrentModificationException -> L2d
            boolean r0 = r0.add(r1)     // Catch: java.util.ConcurrentModificationException -> L2d
            goto Lf
        L2b:
            r0 = r4
            return r0
        L2d:
            r4 = move-exception
            goto L0
        */
        throw new UnsupportedOperationException("Method not decompiled: hudson.Functions.getLoggerNames():java.util.List");
    }

    public String getPasswordValue(Object o) {
        if (o == null) {
            return null;
        }
        if (o.equals("<DEFAULT>")) {
            return o.toString();
        }
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (((o instanceof Secret) || Secret.BLANK_NONSECRET_PASSWORD_FIELDS_WITHOUT_ITEM_CONFIGURE) && req != null) {
            if (NON_RECURSIVE_PASSWORD_MASKING_PERMISSION_CHECK) {
                List<Ancestor> ancestors = req.getAncestors();
                String closestAncestor = ancestors.isEmpty() ? "unknown" : ((Ancestor) ancestors.getLast()).getObject().getClass().getName();
                Item item = (Item) req.findAncestorObject(Item.class);
                if (item != null && !item.hasPermission(Item.CONFIGURE)) {
                    PasswordMasking.recordMasking(item.getClass().getName(), closestAncestor, getJellyViewsInformationForCurrentRequest());
                    return "********";
                }
                Computer computer = (Computer) req.findAncestorObject(Computer.class);
                if (computer != null && !computer.hasPermission(Computer.CONFIGURE)) {
                    PasswordMasking.recordMasking(computer.getClass().getName(), closestAncestor, getJellyViewsInformationForCurrentRequest());
                    return "********";
                }
            } else {
                List<Ancestor> ancestors2 = req.getAncestors();
                String closestAncestor2 = ancestors2.isEmpty() ? "unknown" : ((Ancestor) ancestors2.getLast()).getObject().getClass().getName();
                Iterator it = Iterators.reverse(ancestors2).iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    Ancestor ancestor = (Ancestor) it.next();
                    Object type = ancestor.getObject();
                    if (type instanceof Item) {
                        Item item2 = (Item) type;
                        if (!item2.hasPermission(Item.CONFIGURE)) {
                            PasswordMasking.recordMasking(item2.getClass().getName(), closestAncestor2, getJellyViewsInformationForCurrentRequest());
                            return "********";
                        }
                    } else if (type instanceof Computer) {
                        Computer computer2 = (Computer) type;
                        if (!computer2.hasPermission(Computer.CONFIGURE)) {
                            PasswordMasking.recordMasking(computer2.getClass().getName(), closestAncestor2, getJellyViewsInformationForCurrentRequest());
                            return "********";
                        }
                    } else if (type instanceof View) {
                        View view = (View) type;
                        if (!view.hasPermission(View.CONFIGURE)) {
                            PasswordMasking.recordMasking(view.getClass().getName(), closestAncestor2, getJellyViewsInformationForCurrentRequest());
                            return "********";
                        }
                    }
                }
            }
        }
        if (o instanceof Secret) {
            return ((Secret) o).getEncryptedValue();
        }
        if (req != null && (Boolean.getBoolean("hudson.hpi.run") || Boolean.getBoolean("hudson.Main.development"))) {
            LOGGER.log(Level.WARNING, () -> {
                return "<f:password/> form control in " + getJellyViewsInformationForCurrentRequest() + " is not backed by hudson.util.Secret. Learn more: https://www.jenkins.io/redirect/hudson.util.Secret";
            });
        }
        if (!Secret.AUTO_ENCRYPT_PASSWORD_CONTROL) {
            return o.toString();
        }
        return Secret.fromString(o.toString()).getEncryptedValue();
    }

    private String getJellyViewsInformationForCurrentRequest() {
        Thread thread = Thread.currentThread();
        String threadName = thread.getName();
        String views = (String) Arrays.stream(threadName.split(" ")).filter(part -> {
            int slash = part.lastIndexOf("/");
            int firstPeriod = part.indexOf(".");
            return slash > 0 && firstPeriod > 0 && slash < firstPeriod;
        }).collect(Collectors.joining(" "));
        if (views == null || views.isBlank()) {
            return threadName;
        }
        return views;
    }

    public List filterDescriptors(Object context, Iterable descriptors) {
        return DescriptorVisibilityFilter.apply(context, descriptors);
    }

    public static boolean getIsUnitTest() {
        return Main.isUnitTest;
    }

    public static boolean isDevelopmentMode() {
        return Main.isDevelopmentMode;
    }

    public static boolean isArtifactsPermissionEnabled() {
        return SystemProperties.getBoolean("hudson.security.ArtifactsPermission");
    }

    public static boolean isWipeOutPermissionEnabled() {
        return SystemProperties.getBoolean("hudson.security.WipeOutPermission");
    }

    @Restricted({NoExternalUse.class})
    public static boolean isStickyPositioningDisabled() {
        String cookieValue = getCookie((HttpServletRequest) Stapler.getCurrentRequest2(), "disableStickyPositioning", (String) null);
        return Boolean.valueOf(cookieValue).booleanValue();
    }

    @Deprecated
    public static String createRenderOnDemandProxy(JellyContext context, String attributesToCapture) {
        return Stapler.getCurrentRequest2().createJavaScriptProxy(new RenderOnDemandClosure(context, attributesToCapture));
    }

    @Restricted({NoExternalUse.class})
    public static StaplerRequest2.RenderOnDemandParameters createRenderOnDemandProxyParameters(JellyContext context, String attributesToCapture) {
        return Stapler.getCurrentRequest2().createJavaScriptProxyParameters(new RenderOnDemandClosure(context, attributesToCapture));
    }

    public static String getCurrentDescriptorByNameUrl() {
        return Descriptor.getCurrentDescriptorByNameUrl();
    }

    public static String setCurrentDescriptorByNameUrl(String value) {
        String o = getCurrentDescriptorByNameUrl();
        Stapler.getCurrentRequest2().setAttribute("currentDescriptorByNameUrl", value);
        return o;
    }

    public static void restoreCurrentDescriptorByNameUrl(String old) {
        Stapler.getCurrentRequest2().setAttribute("currentDescriptorByNameUrl", old);
    }

    public static List<String> getRequestHeaders(String name) {
        List<String> r = new ArrayList<>();
        Enumeration e = Stapler.getCurrentRequest2().getHeaders(name);
        while (e.hasMoreElements()) {
            r.add(e.nextElement().toString());
        }
        return r;
    }

    public static Object rawHtml(Object o) {
        if (o == null) {
            return null;
        }
        return new RawHtmlArgument(o);
    }

    public static ArrayList<CLICommand> getCLICommands() {
        ArrayList<CLICommand> all = new ArrayList<>((Collection<? extends CLICommand>) CLICommand.all());
        all.sort(Comparator.comparing((v0) -> {
            return v0.getName();
        }));
        return all;
    }

    public static String getAvatar(User user, String avatarSize) {
        return UserAvatarResolver.resolve(user, avatarSize);
    }

    @Deprecated
    public String getUserAvatar(User user, String avatarSize) {
        return getAvatar(user, avatarSize);
    }

    public static String humanReadableByteSize(long size) {
        String measure = "B";
        if (size < 1024) {
            return size + " " + size;
        }
        double number = size;
        if (number >= 1024.0d) {
            number /= 1024.0d;
            measure = "KiB";
            if (number >= 1024.0d) {
                number /= 1024.0d;
                measure = "MiB";
                if (number >= 1024.0d) {
                    number /= 1024.0d;
                    measure = "GiB";
                    if (number >= 1024.0d) {
                        number /= 1024.0d;
                        measure = "TiB";
                    }
                }
            }
        }
        DecimalFormat format = new DecimalFormat("#0.00");
        return format.format(number) + " " + measure;
    }

    public static String breakableString(final String plain) {
        if (plain == null) {
            return null;
        }
        return plain.replaceAll("([\\p{Punct}&&[^;]]+\\w)", "<wbr>$1").replaceAll("([^\\p{Punct}\\s-]{20})(?=[^\\p{Punct}\\s-]{10})", "$1<wbr>");
    }

    public static void advertiseHeaders(HttpServletResponse rsp) {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) {
            rsp.setHeader("X-Hudson", "1.395");
            rsp.setHeader("X-Jenkins", Jenkins.VERSION);
            rsp.setHeader("X-Jenkins-Session", Jenkins.SESSION_HASH);
        }
    }

    @Deprecated
    public static void advertiseHeaders(javax.servlet.http.HttpServletResponse rsp) {
        advertiseHeaders(HttpServletResponseWrapper.toJakartaHttpServletResponse(rsp));
    }

    @Restricted({NoExternalUse.class})
    public static boolean isContextMenuVisible(Action a) {
        if (a instanceof ModelObjectWithContextMenu.ContextMenuVisibility) {
            return ((ModelObjectWithContextMenu.ContextMenuVisibility) a).isVisible();
        }
        return true;
    }

    @Restricted({NoExternalUse.class})
    public static Icon tryGetIcon(String iconGuess) {
        if (iconGuess == null || iconGuess.startsWith("symbol-")) {
            return null;
        }
        Icon iconMetadata = IconSet.icons.getIconByClassSpec(iconGuess);
        if (iconMetadata == null && iconGuess.contains(" ")) {
            iconMetadata = IconSet.icons.getIconByClassSpec(filterIconNameClasses(iconGuess));
        }
        if (iconMetadata == null) {
            iconMetadata = IconSet.icons.getIconByClassSpec(IconSet.toNormalizedIconNameClass(iconGuess) + " icon-md");
        }
        if (iconMetadata == null) {
            iconMetadata = IconSet.icons.getIconByUrl(iconGuess);
        }
        return iconMetadata;
    }

    @NonNull
    private static String filterIconNameClasses(@NonNull String classNames) {
        return (String) Arrays.stream(classNames.split(" ")).filter(className -> {
            return className.startsWith("icon-");
        }).collect(Collectors.joining(" "));
    }

    @Restricted({NoExternalUse.class})
    public static String extractPluginNameFromIconSrc(String iconSrc) {
        if (iconSrc == null || !iconSrc.contains("plugin-")) {
            return "";
        }
        String[] arr = iconSrc.split(" ");
        for (String element : arr) {
            if (element.startsWith("plugin-")) {
                return element.replaceFirst("plugin-", "");
            }
        }
        return "";
    }

    @Restricted({NoExternalUse.class})
    public static String tryGetIconPath(String iconGuess, JellyContext context) {
        String iconSource;
        if (iconGuess == null) {
            return null;
        }
        if (iconGuess.startsWith("symbol-")) {
            return iconGuess;
        }
        StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
        String rootURL = currentRequest.getContextPath();
        Icon iconMetadata = tryGetIcon(iconGuess);
        if (iconMetadata != null) {
            iconSource = IconSet.tryTranslateTangoIconToSymbol(iconMetadata.getClassSpec(), () -> {
                return iconMetadata.getQualifiedUrl(context);
            });
        } else {
            iconSource = guessIcon(iconGuess, rootURL);
        }
        return iconSource;
    }

    static String guessIcon(String iconGuess, String rootURL) {
        String iconSource;
        if (iconGuess.startsWith("http://") || iconGuess.startsWith("https://")) {
            iconSource = iconGuess;
        } else {
            if (!iconGuess.startsWith("/")) {
                iconGuess = "/" + iconGuess;
            }
            if (iconGuess.startsWith(rootURL) && ((!rootURL.equals("/images") && !rootURL.equals("/plugin")) || iconGuess.startsWith(rootURL + rootURL))) {
                iconGuess = iconGuess.substring(rootURL.length());
            }
            iconSource = rootURL + ((iconGuess.startsWith("/images/") || iconGuess.startsWith("/plugin/")) ? getResourcePath() : "") + iconGuess;
        }
        return iconSource;
    }

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"PREDICTABLE_RANDOM"}, justification = "True randomness isn't necessary for form item IDs")
    public static String generateItemId() {
        return String.valueOf(Math.floor(Math.random() * 3000.0d));
    }

    @Restricted({NoExternalUse.class})
    public static String convertActionsToJson(String baseUrl, List<Action> actions) {
        ModelObjectWithContextMenu.ContextMenu contextMenu = new ModelObjectWithContextMenu.ContextMenu();
        contextMenu.addAll(actions.stream().filter(action -> {
            if (action.getIconFileName() == null) {
                if (action instanceof IconSpec) {
                    IconSpec iconSpec = (IconSpec) action;
                    if (iconSpec.getIconClassName() != null) {
                    }
                }
                return false;
            }
            return true;
        }).filter(action2 -> {
            return action2.getGroup().getOrder() < Group.FIRST_IN_MENU.getOrder();
        }).toList());
        JSONObject jsonObject = JSONObject.fromObject(contextMenu);
        jsonObject.put("url", Util.ensureEndsWith(baseUrl, "/"));
        return jsonObject.toString();
    }

    @Restricted({NoExternalUse.class})
    public static Map<DetailGroup, List<Detail>> getDetailsFor(Actionable object) {
        ExtensionList<DetailGroup> groupsExtensionList = ExtensionList.lookup(DetailGroup.class);
        List<ExtensionComponent<DetailGroup>> components = groupsExtensionList.getComponents();
        Map<String, Double> detailGroupOrdinal = (Map) components.stream().collect(Collectors.toMap(k -> {
            return ((DetailGroup) k.getInstance()).getClass().getName();
        }, (v0) -> {
            return v0.ordinal();
        }));
        Map<DetailGroup, List<Detail>> result = new TreeMap<>((Comparator<? super DetailGroup>) Comparator.comparingDouble(d -> {
            return ((Double) detailGroupOrdinal.get(d.getClass().getName())).doubleValue();
        }));
        for (DetailFactory taf : DetailFactory.factoriesFor(object.getClass())) {
            List<Detail> details = taf.createFor(object);
            details.forEach(e -> {
                ((List) result.computeIfAbsent(e.getGroup(), k2 -> {
                    return new ArrayList();
                })).add(e);
            });
        }
        for (Map.Entry<DetailGroup, List<Detail>> entry : result.entrySet()) {
            List<Detail> detailList = entry.getValue();
            detailList.sort(Comparator.comparingInt((v0) -> {
                return v0.getOrder();
            }).reversed());
        }
        return result;
    }

    @Restricted({NoExternalUse.class})
    public static ExtensionList<SearchFactory> getSearchFactories() {
        return SearchFactory.all();
    }

    @Restricted({NoExternalUse.class})
    public static String translateModifierKeysForUsersPlatform(String keyboardShortcut) {
        StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
        currentRequest.getWebApp().getDispatchValidator().allowDispatch(currentRequest, Stapler.getCurrentResponse2());
        String userAgent = currentRequest.getHeader("User-Agent");
        if (userAgent != null) {
            List<String> platformsThatUseCommand = List.of("MAC", "IPHONE", "IPAD");
            boolean useCmdKey = platformsThatUseCommand.stream().anyMatch(e -> {
                return userAgent.toUpperCase().contains(e);
            });
            return keyboardShortcut.replace("CMD", useCmdKey ? "⌘" : "CTRL");
        }
        return keyboardShortcut;
    }

    @Restricted({NoExternalUse.class})
    public static String formatMessage(String format, Object args) {
        if (format == null) {
            return args.toString();
        }
        return MessageFormat.format(format, args);
    }
}
