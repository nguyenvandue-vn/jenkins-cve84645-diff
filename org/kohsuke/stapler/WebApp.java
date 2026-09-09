package org.kohsuke.stapler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.servlet.ServletContextWrapper;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.servlet.ServletContext;
import org.kohsuke.stapler.FunctionList;
import org.kohsuke.stapler.HttpResponseRenderer;
import org.kohsuke.stapler.bind.BoundObjectTable;
import org.kohsuke.stapler.event.FilteredDispatchTriggerListener;
import org.kohsuke.stapler.event.FilteredDoActionTriggerListener;
import org.kohsuke.stapler.event.FilteredFieldTriggerListener;
import org.kohsuke.stapler.event.FilteredGetterTriggerListener;
import org.kohsuke.stapler.lang.FieldRef;
import org.kohsuke.stapler.lang.KInstance;
import org.kohsuke.stapler.lang.Klass;
import org.kohsuke.stapler.lang.KlassNavigator;

/* loaded from: WebApp.class */
public class WebApp {

    @Deprecated
    public final ServletContext context;
    private final jakarta.servlet.ServletContext servletContext;
    private volatile ClassLoader classLoader;
    private volatile ClassValue<MetaClass> classMap;
    private DispatchersFilter dispatchersFilter;
    private JsonInErrorMessageSanitizer jsonInErrorMessageSanitizer;

    @Deprecated
    public final Map<Class, Class[]> wrappers = new HashMap();
    public final Map<String, String> defaultEncodingForStaticResources = new HashMap();
    public final List<Facet> facets = new Vector();
    public final List<BindInterceptor> bindInterceptors = new CopyOnWriteArrayList();

    @Deprecated
    public final Map<String, String> mimeTypes = new Hashtable();
    public final BoundObjectTable boundObjectTable = new BoundObjectTable();
    private final CopyOnWriteArrayList<HttpResponseRenderer> responseRenderers = new CopyOnWriteArrayList<>();
    private CrumbIssuer crumbIssuer = CrumbIssuer.NONE;
    private final ConcurrentMap<String, Stapler> servlets = new ConcurrentHashMap();
    private FunctionList.Filter filterForGetMethods = FunctionList.Filter.ALWAYS_OK;
    private FunctionList.Filter filterForDoActions = FunctionList.Filter.ALWAYS_OK;
    private FieldRef.Filter filterForFields = FieldRef.Filter.ALWAYS_OK;
    private FilteredDoActionTriggerListener filteredDoActionTriggerListener = FilteredDoActionTriggerListener.JUST_WARN;
    private FilteredGetterTriggerListener filteredGetterTriggerListener = FilteredGetterTriggerListener.JUST_WARN;
    private FilteredFieldTriggerListener filteredFieldTriggerListener = FilteredFieldTriggerListener.JUST_WARN;
    private DispatchValidator dispatchValidator = DispatchValidator.DEFAULT;
    private FilteredDispatchTriggerListener filteredDispatchTriggerListener = FilteredDispatchTriggerListener.JUST_WARN;

    public static WebApp get(jakarta.servlet.ServletContext context) {
        Object o = context.getAttribute(WebApp.class.getName());
        if (o == null) {
            synchronized (WebApp.class) {
                o = context.getAttribute(WebApp.class.getName());
                if (o == null) {
                    o = new WebApp(context);
                    context.setAttribute(WebApp.class.getName(), o);
                }
            }
        }
        return (WebApp) o;
    }

    @Deprecated
    public static WebApp get(ServletContext context) {
        return get(ServletContextWrapper.toJakartaServletContext(context));
    }

    public WebApp(jakarta.servlet.ServletContext context) {
        this.servletContext = context;
        this.context = context != null ? ServletContextWrapper.fromJakartServletContext(context) : null;
        this.facets.addAll(Facet.discoverExtensions(Facet.class, new ClassLoader[]{Thread.currentThread().getContextClassLoader(), getClass().getClassLoader()}));
        this.responseRenderers.add(new HttpResponseRenderer.Default());
    }

    public Object getApp() {
        return this.servletContext.getAttribute("app");
    }

    public void setApp(Object app) {
        this.servletContext.setAttribute("app", app);
    }

    public jakarta.servlet.ServletContext getServletContext() {
        return this.servletContext;
    }

    public CrumbIssuer getCrumbIssuer() {
        return this.crumbIssuer;
    }

    public void setCrumbIssuer(CrumbIssuer crumbIssuer) {
        this.crumbIssuer = crumbIssuer;
    }

    public CopyOnWriteArrayList<HttpResponseRenderer> getResponseRenderers() {
        return this.responseRenderers;
    }

    public ClassLoader getClassLoader() {
        ClassLoader cl = this.classLoader;
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (cl == null) {
            cl = Stapler.class.getClassLoader();
        }
        return cl;
    }

    public <T extends Facet> T getFacet(Class<T> type) {
        for (Facet f : this.facets) {
            if (type == f.getClass()) {
                return type.cast(f);
            }
        }
        return null;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    private ClassValue<MetaClass> getClassMap() {
        ClassValue<MetaClass> _classMap = this.classMap;
        if (_classMap == null) {
            synchronized (this) {
                _classMap = this.classMap;
                if (_classMap == null) {
                    ClassValue<MetaClass> classValue = new ClassValue<MetaClass>() { // from class: org.kohsuke.stapler.WebApp.1
                        @Override // java.lang.ClassValue
                        protected /* bridge */ /* synthetic */ MetaClass computeValue(Class c) {
                            return computeValue((Class<?>) c);
                        }

                        /* JADX WARN: Can't rename method to resolve collision */
                        @Override // java.lang.ClassValue
                        protected MetaClass computeValue(Class<?> c) {
                            return new MetaClass(WebApp.this, Klass.java(c));
                        }
                    };
                    _classMap = classValue;
                    this.classMap = classValue;
                }
            }
        }
        return _classMap;
    }

    public MetaClass getMetaClass(Class c) {
        return getMetaClass(Klass.java(c));
    }

    public MetaClass getMetaClass(Klass<?> c) {
        if (c == null) {
            return null;
        }
        if (c.navigator == KlassNavigator.JAVA) {
            return getClassMap().get(c.toJavaClass());
        }
        return new MetaClass(this, c);
    }

    public MetaClass getMetaClass(Object o) {
        return getMetaClass(getKlass(o));
    }

    public Klass<?> getKlass(Object o) {
        if (o instanceof KInstance) {
            KInstance ki = (KInstance) o;
            Klass k = ki.getKlass();
            if (k != null) {
                return k;
            }
        }
        for (Facet f : this.facets) {
            Klass<?> k2 = f.getKlass(o);
            if (k2 != null) {
                return k2;
            }
        }
        return Klass.java(o.getClass());
    }

    @Deprecated
    public void clearScripts(Class<? extends AbstractTearOff> clazz) {
        clearMetaClassCache();
    }

    @SuppressFBWarnings(value = {"USO_UNSAFE_METHOD_SYNCHRONIZATION"}, justification = "Warning is unreasonable")
    public synchronized void clearMetaClassCache() {
        this.classMap = null;
    }

    void addStaplerServlet(String servletName, Stapler servlet) {
        if (servletName == null) {
            servletName = "";
        }
        this.servlets.put(servletName, servlet);
    }

    public Stapler getSomeStapler() {
        return this.servlets.values().iterator().next();
    }

    public static WebApp getCurrent() {
        return Stapler.getCurrent().getWebApp();
    }

    public FunctionList.Filter getFilterForGetMethods() {
        return this.filterForGetMethods;
    }

    public void setFilterForGetMethods(FunctionList.Filter filterForGetMethods) {
        this.filterForGetMethods = filterForGetMethods;
    }

    public FunctionList.Filter getFilterForDoActions() {
        return this.filterForDoActions;
    }

    public void setFilterForDoActions(FunctionList.Filter filterForDoActions) {
        this.filterForDoActions = filterForDoActions;
    }

    public FieldRef.Filter getFilterForFields() {
        return this.filterForFields;
    }

    public void setFilterForFields(FieldRef.Filter filterForFields) {
        this.filterForFields = filterForFields;
    }

    public DispatchersFilter getDispatchersFilter() {
        return this.dispatchersFilter;
    }

    public void setDispatchersFilter(DispatchersFilter dispatchersFilter) {
        this.dispatchersFilter = dispatchersFilter;
    }

    public FilteredDoActionTriggerListener getFilteredDoActionTriggerListener() {
        return this.filteredDoActionTriggerListener;
    }

    public void setFilteredDoActionTriggerListener(FilteredDoActionTriggerListener filteredDoActionTriggerListener) {
        if (filteredDoActionTriggerListener == null) {
            this.filteredDoActionTriggerListener = FilteredDoActionTriggerListener.JUST_WARN;
        } else {
            this.filteredDoActionTriggerListener = filteredDoActionTriggerListener;
        }
    }

    public FilteredGetterTriggerListener getFilteredGetterTriggerListener() {
        return this.filteredGetterTriggerListener;
    }

    public void setFilteredGetterTriggerListener(FilteredGetterTriggerListener filteredGetterTriggerListener) {
        if (filteredGetterTriggerListener == null) {
            this.filteredGetterTriggerListener = FilteredGetterTriggerListener.JUST_WARN;
        } else {
            this.filteredGetterTriggerListener = filteredGetterTriggerListener;
        }
    }

    public FilteredFieldTriggerListener getFilteredFieldTriggerListener() {
        return this.filteredFieldTriggerListener;
    }

    public void setFilteredFieldTriggerListener(FilteredFieldTriggerListener filteredFieldTriggerListener) {
        if (filteredFieldTriggerListener == null) {
            this.filteredFieldTriggerListener = FilteredFieldTriggerListener.JUST_WARN;
        } else {
            this.filteredFieldTriggerListener = filteredFieldTriggerListener;
        }
    }

    public JsonInErrorMessageSanitizer getJsonInErrorMessageSanitizer() {
        if (this.jsonInErrorMessageSanitizer == null) {
            return JsonInErrorMessageSanitizer.NOOP;
        }
        return this.jsonInErrorMessageSanitizer;
    }

    public void setJsonInErrorMessageSanitizer(JsonInErrorMessageSanitizer jsonInErrorMessageSanitizer) {
        this.jsonInErrorMessageSanitizer = jsonInErrorMessageSanitizer;
    }

    public DispatchValidator getDispatchValidator() {
        if (this.dispatchValidator == null) {
            this.dispatchValidator = DispatchValidator.DEFAULT;
        }
        return this.dispatchValidator;
    }

    public void setDispatchValidator(DispatchValidator dispatchValidator) {
        this.dispatchValidator = dispatchValidator;
    }

    public FilteredDispatchTriggerListener getFilteredDispatchTriggerListener() {
        if (this.filteredDispatchTriggerListener == null) {
            this.filteredDispatchTriggerListener = FilteredDispatchTriggerListener.JUST_WARN;
        }
        return this.filteredDispatchTriggerListener;
    }

    public void setFilteredDispatchTriggerListener(FilteredDispatchTriggerListener filteredDispatchTriggerListener) {
        this.filteredDispatchTriggerListener = filteredDispatchTriggerListener;
    }
}
