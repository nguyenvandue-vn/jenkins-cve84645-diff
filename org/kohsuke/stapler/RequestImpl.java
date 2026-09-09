package org.kohsuke.stapler;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileUploadByteCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadFileCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletDiskFileUpload;
import org.jvnet.tiger_types.Lister;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.bind.Bound;
import org.kohsuke.stapler.bind.BoundObjectTable;
import org.kohsuke.stapler.lang.Klass;
import org.kohsuke.stapler.lang.MethodRef;

/* loaded from: RequestImpl.class */
public class RequestImpl extends HttpServletRequestWrapper implements StaplerRequest2 {
    public final TokenList tokens;
    public final List<AncestorImpl> ancestors;
    private final List<Ancestor> ancestorsView;
    public final Stapler stapler;
    private final String originalRequestURI;
    private JSONObject structuredForm;
    private Map<String, FileItem> parsedFormData;
    private Map<String, String> parsedFormDataFormFields;
    private BindInterceptor bindInterceptor;
    private static List<String> ALLOWED_HTTP_VERBS_FOR_FORMS;
    private static int FILEUPLOAD_MAX_FILES;
    private static long FILEUPLOAD_MAX_FILE_SIZE;
    private static long FILEUPLOAD_MAX_SIZE;
    private static final Logger LOGGER;
    static final /* synthetic */ boolean $assertionsDisabled;

    static {
        $assertionsDisabled = !RequestImpl.class.desiredAssertionStatus();
        FILEUPLOAD_MAX_FILES = Integer.getInteger(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES", 1000).intValue();
        FILEUPLOAD_MAX_FILE_SIZE = Long.getLong(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE", -1L).longValue();
        FILEUPLOAD_MAX_SIZE = Long.getLong(RequestImpl.class.getName() + ".FILEUPLOAD_MAX_SIZE", -1L).longValue();
        ALLOWED_HTTP_VERBS_FOR_FORMS = (List) Arrays.stream(System.getProperty(RequestImpl.class.getName() + ".ALLOWED_HTTP_VERBS_FOR_FORMS", "POST").split(",")).map((v0) -> {
            return v0.trim();
        }).collect(Collectors.toList());
        LOGGER = Logger.getLogger(RequestImpl.class.getName());
    }

    public RequestImpl(Stapler stapler, HttpServletRequest request, List<AncestorImpl> ancestors, TokenList tokens) {
        super(request);
        this.bindInterceptor = BindInterceptor.NOOP;
        this.stapler = stapler;
        this.ancestors = ancestors;
        this.ancestorsView = Collections.unmodifiableList(ancestors);
        this.tokens = tokens;
        this.originalRequestURI = request.getRequestURI();
    }

    @Deprecated
    public RequestImpl(Stapler stapler, javax.servlet.http.HttpServletRequest request, List<AncestorImpl> ancestors, TokenList tokens) {
        this(stapler, io.jenkins.servlet.http.HttpServletRequestWrapper.toJakartaHttpServletRequest(request), ancestors, tokens);
    }

    public boolean isJavaScriptProxyCall() {
        String ct = getContentType();
        return ct != null && ct.startsWith("application/x-stapler-method-invocation");
    }

    public BoundObjectTable getBoundObjectTable() {
        return this.stapler.getWebApp().boundObjectTable;
    }

    public String createJavaScriptProxy(Object toBeExported) {
        return getBoundObjectTable().bind(toBeExported).getProxyScript();
    }

    public StaplerRequest2.RenderOnDemandParameters createJavaScriptProxyParameters(Object toBeExported) {
        Bound bound = getBoundObjectTable().bind(toBeExported);
        return new StaplerRequest2.RenderOnDemandParameters("makeStaplerProxy", bound.getURL(), getWebApp().getCrumbIssuer().issueCrumb(), bound.getBoundJavaScriptUrlNames());
    }

    public Stapler getStapler() {
        return this.stapler;
    }

    public WebApp getWebApp() {
        return this.stapler.getWebApp();
    }

    public String getRestOfPath() {
        return this.tokens.assembleRestOfPath();
    }

    public String getOriginalRestOfPath() {
        return this.tokens.assembleOriginalRestOfPath();
    }

    public ServletContext getServletContext() {
        return this.stapler.getServletContext();
    }

    public String getParameter(String name) {
        if (isMultipart()) {
            Map<String, String> data = getFormDataFormFields();
            String value = data.get(name);
            if (value != null) {
                return value;
            }
        }
        return super.getParameter(name);
    }

    public Map<String, String[]> getParameterMap() {
        String[] values;
        Map<String, String[]> parameterMap = super.getParameterMap();
        if (isMultipart()) {
            Map<String, String> data = getFormDataFormFields();
            Map<String, String[]> parameterMap2 = new HashMap<>(parameterMap);
            for (Map.Entry<String, String> e : data.entrySet()) {
                String[] values2 = parameterMap2.get(e.getKey());
                if (values2 == null) {
                    values = new String[]{e.getValue()};
                } else {
                    int len = values2.length;
                    String[] moreValues = (String[]) Arrays.copyOf(values2, len + 1);
                    moreValues[len] = e.getValue();
                    values = moreValues;
                }
                parameterMap2.put(e.getKey(), values);
            }
            parameterMap = Collections.unmodifiableMap(parameterMap2);
        }
        return parameterMap;
    }

    public Enumeration<String> getParameterNames() {
        if (!isMultipart()) {
            return super.getParameterNames();
        }
        Map<String, String> data = getFormDataFormFields();
        if (data.isEmpty()) {
            return super.getParameterNames();
        }
        List<String> paramNames = Collections.list(super.getParameterNames());
        paramNames.addAll(data.keySet());
        return Collections.enumeration(paramNames);
    }

    public String[] getParameterValues(String name) {
        if (!isMultipart()) {
            return super.getParameterValues(name);
        }
        Map<String, String> data = getFormDataFormFields();
        if (data.isEmpty()) {
            return super.getParameterValues(name);
        }
        String formFieldVal = data.get(name);
        if (formFieldVal == null) {
            return super.getParameterValues(name);
        }
        String[] values = super.getParameterValues(name);
        if (values == null) {
            values = new String[0];
        }
        String[] extValues = new String[values.length + 1];
        System.arraycopy(values, 0, extValues, 0, values.length);
        extValues[extValues.length - 1] = formFieldVal;
        return extValues;
    }

    public String getRequestURIWithQueryString() {
        String s = getRequestURI();
        String q = getQueryString();
        if (q != null) {
            s = s + "?" + q;
        }
        return s;
    }

    public StringBuffer getRequestURLWithQueryString() {
        StringBuffer s = getRequestURL();
        String q = getQueryString();
        if (q != null) {
            s.append('?').append(q);
        }
        return s;
    }

    public RequestDispatcher getView(Object it, String viewName) throws IOException {
        return getView(Klass.java(it.getClass()), it, viewName);
    }

    public RequestDispatcher getView(Class clazz, String viewName) throws IOException {
        return getView(Klass.java(clazz), null, viewName);
    }

    public RequestDispatcher getView(Klass<?> clazz, String viewName) throws IOException {
        return getView(clazz, null, viewName);
    }

    public RequestDispatcher getView(Klass<?> clazz, Object it, String viewName) throws IOException {
        for (Facet f : this.stapler.getWebApp().facets) {
            RequestDispatcher rd = f.createRequestDispatcher(this, clazz, it, viewName);
            if (rd != null) {
                return rd;
            }
        }
        return null;
    }

    public String getRootPath() {
        StringBuffer buf = super.getRequestURL();
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            idx += buf.substring(idx).indexOf("/") + 1;
        }
        buf.setLength(idx - 1);
        buf.append(super.getContextPath());
        return buf.toString();
    }

    public String getReferer() {
        return getHeader("Referer");
    }

    public List<Ancestor> getAncestors() {
        return this.ancestorsView;
    }

    public Ancestor findAncestor(Class type) {
        for (int i = this.ancestors.size() - 1; i >= 0; i--) {
            AncestorImpl a = this.ancestors.get(i);
            Object o = a.getObject();
            if (type.isInstance(o)) {
                return a;
            }
        }
        return null;
    }

    public <T> T findAncestorObject(Class<T> type) {
        Ancestor a = findAncestor((Class) type);
        if (a == null) {
            return null;
        }
        return type.cast(a.getObject());
    }

    public Ancestor findAncestor(Object anc) {
        for (int i = this.ancestors.size() - 1; i >= 0; i--) {
            AncestorImpl a = this.ancestors.get(i);
            Object o = a.getObject();
            if (o == anc) {
                return a;
            }
        }
        return null;
    }

    public boolean hasParameter(String name) {
        return getParameter(name) != null;
    }

    public String getOriginalRequestURI() {
        return this.originalRequestURI;
    }

    public boolean checkIfModified(long lastModified, StaplerResponse2 rsp) {
        return checkIfModified(lastModified, rsp, 0L);
    }

    public boolean checkIfModified(long lastModified, StaplerResponse2 rsp, long expiration) {
        if (lastModified <= 0) {
            return false;
        }
        String since = getHeader("If-Modified-Since");
        SimpleDateFormat format = (SimpleDateFormat) Stapler.HTTP_DATE_FORMAT.get();
        if (since != null) {
            try {
                long ims = format.parse(since).getTime();
                if (lastModified < ims + 1000) {
                    rsp.setStatus(304);
                    return true;
                }
            } catch (NumberFormatException | ParseException e) {
            }
        }
        String tm = format.format(new Date(lastModified));
        rsp.setHeader("Last-Modified", tm);
        if (expiration == 0) {
            rsp.setHeader("Expires", tm);
            return false;
        }
        rsp.setHeader("Expires", format.format(new Date(new Date().getTime() + expiration)));
        return false;
    }

    public boolean checkIfModified(Date timestampOfResource, StaplerResponse2 rsp) {
        return checkIfModified(timestampOfResource.getTime(), rsp);
    }

    public boolean checkIfModified(Calendar timestampOfResource, StaplerResponse2 rsp) {
        return checkIfModified(timestampOfResource.getTimeInMillis(), rsp);
    }

    public BindInterceptor getBindInterceptor() {
        return this.bindInterceptor;
    }

    public BindInterceptor setBindListener(BindInterceptor bindListener) {
        return setBindInterceptor(bindListener);
    }

    public BindInterceptor setBindInterceptpr(BindInterceptor bindListener) {
        return setBindInterceptor(bindListener);
    }

    public BindInterceptor setBindInterceptor(BindInterceptor bindListener) {
        BindInterceptor o = this.bindInterceptor;
        this.bindInterceptor = bindListener;
        return o;
    }

    public void bindParameters(Object bean) {
        bindParameters(bean, "");
    }

    public void bindParameters(Object bean, String prefix) {
        Enumeration e = getParameterNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            if (name.startsWith(prefix)) {
                fill(bean, name.substring(prefix.length()), getParameter(name));
            }
        }
    }

    public <T> List<T> bindParametersToList(Class<T> type, String prefix) {
        ArrayList arrayList = new ArrayList();
        int len = Integer.MAX_VALUE;
        Enumeration e = getParameterNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            if (name.startsWith(prefix)) {
                len = Math.min(len, getParameterValues(name).length);
            }
        }
        if (len == Integer.MAX_VALUE) {
            return arrayList;
        }
        try {
            new ClassDescriptor(type, new Class[0]).loadConstructorParamNames();
            for (int i = 0; i < len; i++) {
                arrayList.add(bindParameters(type, prefix, i));
            }
        } catch (NoStaplerConstructorException e2) {
            for (int i2 = 0; i2 < len; i2++) {
                try {
                    T t = type.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
                    arrayList.add(t);
                    Enumeration e3 = getParameterNames();
                    while (e3.hasMoreElements()) {
                        String name2 = e3.nextElement();
                        if (name2.startsWith(prefix)) {
                            fill(t, name2.substring(prefix.length()), getParameterValues(name2)[i2]);
                        }
                    }
                } catch (IllegalAccessException x) {
                    throw new IllegalAccessError(x.getMessage());
                } catch (InstantiationException x2) {
                    throw new InstantiationError(x2.getMessage());
                } catch (NoSuchMethodException x3) {
                    throw new NoSuchMethodError(x3.getMessage());
                } catch (InvocationTargetException x4) {
                    Throwable t2 = x4.getCause();
                    if (t2 instanceof RuntimeException) {
                        throw ((RuntimeException) t2);
                    }
                    if (t2 instanceof IOException) {
                        throw new UncheckedIOException((IOException) t2);
                    }
                    if (t2 instanceof Exception) {
                        throw new RuntimeException(t2);
                    }
                    if (t2 instanceof Error) {
                        throw ((Error) t2);
                    }
                    throw new Error(x4);
                }
            }
        }
        return arrayList;
    }

    public <T> T bindParameters(Class<T> cls, String str) {
        return (T) bindParameters(cls, str, 0);
    }

    public <T> T bindParameters(Class<T> cls, String str, int i) {
        String str2;
        String[] loadConstructorParamNames = new ClassDescriptor(cls, new Class[0]).loadConstructorParamNames();
        Object[] objArr = new Object[loadConstructorParamNames.length];
        Constructor<T> findConstructor = findConstructor(cls, loadConstructorParamNames.length);
        Class<?>[] parameterTypes = findConstructor.getParameterTypes();
        for (int i2 = 0; i2 < loadConstructorParamNames.length; i2++) {
            String[] parameterValues = getParameterValues(str + loadConstructorParamNames[i2]);
            if (parameterValues != null) {
                str2 = parameterValues[i];
            } else {
                str2 = null;
            }
            Converter lookupConverter = Stapler.lookupConverter(parameterTypes[i2]);
            if (lookupConverter == null) {
                throw new IllegalArgumentException("Unable to convert to " + String.valueOf(parameterTypes[i2]));
            }
            objArr[i2] = lookupConverter.convert(parameterTypes[i2], str2);
        }
        return (T) invokeConstructor(findConstructor, objArr);
    }

    public <T> T bindJSON(Class<T> type, JSONObject src) {
        return type.cast(bindJSON(type, type, src));
    }

    public Object bindJSON(Type type, Class erasure, Object json) {
        return new TypePair(type, erasure).convertJSON(json);
    }

    public void bindJSON(Object bean, JSONObject src) {
        try {
            for (String key : src.keySet()) {
                TypePair type = getPropertyType(bean, key);
                if (type != null) {
                    try {
                        fill(bean, key, type.convertJSON(src.get(key)));
                    } catch (WrongTypeException e) {
                        throw new IllegalArgumentException(String.format("Error binding field %s: %s", key, e.getMessage()));
                    }
                }
            }
        } catch (IllegalAccessException e2) {
            IllegalAccessError x = new IllegalAccessError(e2.getMessage());
            x.initCause(e2);
            throw x;
        } catch (InvocationTargetException x2) {
            Throwable e3 = x2.getTargetException();
            if (e3 instanceof RuntimeException) {
                throw ((RuntimeException) e3);
            }
            if (e3 instanceof Error) {
                throw ((Error) e3);
            }
            throw new RuntimeException(x2);
        }
    }

    public <T> List<T> bindJSONToList(Class<T> type, Object src) {
        ArrayList arrayList = new ArrayList();
        if (src instanceof JSONObject) {
            JSONObject j = (JSONObject) src;
            arrayList.add(bindJSON((Class) type, j));
        }
        if (src instanceof JSONArray) {
            JSONArray a = (JSONArray) src;
            Iterator it = a.iterator();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof JSONObject) {
                    JSONObject j2 = (JSONObject) o;
                    arrayList.add(bindJSON((Class) type, j2));
                }
            }
        }
        return arrayList;
    }

    private <T> T invokeConstructor(Constructor<T> c, Object[] args) {
        try {
            return c.newInstance(args);
        } catch (IllegalAccessException e) {
            IllegalAccessError x = new IllegalAccessError(e.getMessage());
            x.initCause(e);
            throw x;
        } catch (IllegalArgumentException e2) {
            throw new IllegalArgumentException("Failed to invoke " + String.valueOf(c) + " with " + String.valueOf(Arrays.asList(args)), e2);
        } catch (InstantiationException e3) {
            InstantiationError x2 = new InstantiationError(e3.getMessage());
            x2.initCause(e3);
            throw x2;
        } catch (InvocationTargetException e4) {
            launderITE(e4);
            return null;
        }
    }

    private <T> Constructor<T> findConstructor(Class<T> type, int length) {
        Constructor[] constructors = type.getConstructors();
        for (Constructor c : constructors) {
            if (c.getAnnotation(DataBoundConstructor.class) != null) {
                if (c.getParameterTypes().length != length) {
                    throw new IllegalArgumentException(String.valueOf(c) + " has @DataBoundConstructor but it doesn't match with your .stapler file. Try clean rebuild");
                }
                return c;
            }
        }
        for (Constructor c2 : constructors) {
            if (c2.getParameterTypes().length == length) {
                return c2;
            }
        }
        throw new IllegalArgumentException(String.valueOf(type) + " does not have a constructor with " + length + " arguments");
    }

    private static void fill(Object bean, String key, Object value) {
        StringTokenizer tokens = new StringTokenizer(key);
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            boolean last = !tokens.hasMoreTokens();
            if (last) {
                try {
                    copyProperty(bean, token, value);
                } catch (IllegalAccessException x) {
                    throw new IllegalAccessError(x.getMessage());
                } catch (NoSuchMethodException e) {
                } catch (InvocationTargetException x2) {
                    Throwable e2 = x2.getTargetException();
                    if (e2 instanceof RuntimeException) {
                        throw ((RuntimeException) e2);
                    }
                    if (e2 instanceof Error) {
                        throw ((Error) e2);
                    }
                    throw new RuntimeException(x2);
                }
            } else {
                bean = BeanUtils.getProperty(bean, token);
            }
        }
    }

    /* loaded from: RequestImpl$TypePair.class */
    private final class TypePair {
        final Type genericType;
        final Class type;

        TypePair(Type genericType, Class type) {
            this.genericType = genericType;
            this.type = type;
        }

        TypePair(final RequestImpl this$0, Field f) {
            this(f.getGenericType(), f.getType());
        }

        public Object convertJSON(Object o) {
            Object r = RequestImpl.this.bindInterceptor.onConvert(this.genericType, this.type, o);
            if (r != BindInterceptor.DEFAULT) {
                return r;
            }
            for (BindInterceptor i : RequestImpl.this.getWebApp().bindInterceptors) {
                Object r2 = i.onConvert(this.genericType, this.type, o);
                if (r2 != BindInterceptor.DEFAULT) {
                    return r2;
                }
            }
            if (o == null || (o instanceof JSONNull)) {
                return ReflectionUtils.getVmDefaultValueFor(this.type);
            }
            if (this.type == JSONArray.class) {
                if (o instanceof JSONArray) {
                    return o;
                }
                JSONArray a = new JSONArray();
                a.add(o);
                return a;
            }
            Lister l = createNullFreeLister(this.type, this.genericType);
            if (o instanceof JSONObject) {
                JSONObject j = (JSONObject) o;
                if (j.isNullObject()) {
                    return ReflectionUtils.getVmDefaultValueFor(this.type);
                }
                if (l == null) {
                    try {
                        Class actualType = this.type;
                        boolean isArray = false;
                        String className = null;
                        if (j.has("stapler-class")) {
                            if (j.optJSONArray("stapler-class") != null) {
                                isArray = true;
                            }
                            className = j.getString("stapler-class");
                            RequestImpl.LOGGER.log(Level.FINE, "stapler-class is deprecated in favor of $class: {0}", className);
                        }
                        if (j.has("$class")) {
                            if (j.optJSONArray("$class") != null) {
                                isArray = true;
                            }
                            className = j.getString("$class");
                        }
                        if (className != null) {
                            if (isArray) {
                                throw new IllegalArgumentException("The frontend sent an unexpected list of classes (" + className + ") rather than an expected single class. See https://www.jenkins.io/doc/developer/views/table-to-div-migration/ for more information.");
                            }
                            ClassLoader cl = RequestImpl.this.stapler.getWebApp().getClassLoader();
                            try {
                                Class subType = cl.loadClass(className);
                                if (!actualType.isAssignableFrom(subType)) {
                                    throw new IllegalArgumentException("Specified type " + String.valueOf(subType) + " is not assignable to the expected " + String.valueOf(actualType));
                                }
                                actualType = subType;
                            } catch (ClassNotFoundException e) {
                                throw new IllegalArgumentException("Class " + className + " is specified in JSON, but no such class found in " + String.valueOf(cl), e);
                            }
                        }
                        return RequestImpl.this.instantiate(actualType, j);
                    } catch (IllegalArgumentException e2) {
                        JSONObject sanitizedJson = RequestImpl.this.getWebApp().getJsonInErrorMessageSanitizer().sanitize(j);
                        throw new IllegalArgumentException("Failed to instantiate " + String.valueOf(this.type) + " from " + String.valueOf(sanitizedJson), e2);
                    }
                }
                if (j.has("stapler-class-bag")) {
                    ClassLoader cl2 = RequestImpl.this.stapler.getWebApp().getClassLoader();
                    for (Map.Entry<String, Object> e3 : j.entrySet()) {
                        Object v = e3.getValue();
                        String className2 = e3.getKey().replace('-', '.');
                        try {
                            Class<?> itemType = cl2.loadClass(className2);
                            if (v instanceof JSONObject) {
                                l.add(RequestImpl.this.bindJSON((Class) itemType, (JSONObject) v));
                            }
                            if (v instanceof JSONArray) {
                                for (Object i2 : RequestImpl.this.bindJSONToList(itemType, (JSONArray) v)) {
                                    l.add(i2);
                                }
                            }
                        } catch (ClassNotFoundException e4) {
                        }
                    }
                } else if (Enum.class.isAssignableFrom(l.itemType)) {
                    for (Map.Entry<String, Object> e5 : j.entrySet()) {
                        Object v2 = e5.getValue();
                        if (v2 != null && (!(v2 instanceof Boolean) || ((Boolean) v2).booleanValue())) {
                            l.add(Enum.valueOf(l.itemType, e5.getKey()));
                        }
                    }
                } else {
                    l.add(RequestImpl.this.new TypePair(l.itemGenericType, l.itemType).convertJSON(j));
                }
                return l.toCollection();
            }
            if (o instanceof JSONArray) {
                JSONArray a2 = (JSONArray) o;
                if (l == null) {
                    throw new WrongTypeException(String.format("Got type array but no lister class found for type %s", this.type));
                }
                TypePair itemType2 = RequestImpl.this.new TypePair(l.itemGenericType, l.itemType);
                Iterator it = a2.iterator();
                while (it.hasNext()) {
                    Object item = it.next();
                    l.add(itemType2.convertJSON(item));
                }
                return l.toCollection();
            }
            if (Enum.class.isAssignableFrom(this.type)) {
                return Enum.valueOf(this.type, o.toString());
            }
            if (l == null) {
                Converter converter = Stapler.lookupConverter(this.type);
                if (converter == null) {
                    if (this.type == Object.class) {
                        return o;
                    }
                    throw new IllegalArgumentException("Unable to convert to " + String.valueOf(this.type));
                }
                return converter.convert(this.type, o);
            }
            Converter converter2 = Stapler.lookupConverter(l.itemType);
            if (converter2 == null) {
                if (l.itemType == Object.class) {
                    l.add(o);
                } else {
                    throw new IllegalArgumentException("Unable to convert to " + String.valueOf(l.itemType));
                }
            } else {
                l.add(converter2.convert(l.itemType, o));
            }
            return l.toCollection();
        }

        private Lister createNullFreeLister(Class itemType, Type itemGenericType) {
            final Lister l = Lister.create(itemType, itemGenericType);
            if (l == null) {
                return null;
            }
            return new Lister(this, l.itemType, l.itemGenericType) { // from class: org.kohsuke.stapler.RequestImpl.TypePair.1
                public Object toCollection() {
                    return l.toCollection();
                }

                public void add(Object o) {
                    if (o != null) {
                        l.add(o);
                    }
                }
            };
        }
    }

    private Object instantiate(Class actualType, JSONObject j) {
        Object r = this.bindInterceptor.instantiate(actualType, j);
        if (r != BindInterceptor.DEFAULT) {
            return r;
        }
        for (BindInterceptor bi : getWebApp().bindInterceptors) {
            Object r2 = bi.instantiate(actualType, j);
            if (r2 != BindInterceptor.DEFAULT) {
                return r2;
            }
        }
        if (actualType == JSONObject.class || actualType == JSON.class) {
            return actualType.cast(j);
        }
        String[] names = new ClassDescriptor(actualType, new Class[0]).loadConstructorParamNames();
        Object[] args = new Object[names.length];
        Constructor c = findConstructor(actualType, names.length);
        Class[] types = c.getParameterTypes();
        Type[] genTypes = c.getGenericParameterTypes();
        for (int i = 0; i < names.length; i++) {
            try {
                args[i] = bindJSON(genTypes[i], types[i], j.get(names[i]));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Failed to convert the " + names[i] + " parameter of the constructor " + String.valueOf(c), e);
            }
        }
        Object o = injectSetters(invokeConstructor(c, args), j, Arrays.asList(names));
        return bindResolve(o, j);
    }

    private Object bindResolve(Object o, JSONObject src) {
        if (o instanceof DataBoundResolvable) {
            DataBoundResolvable dbr = (DataBoundResolvable) o;
            o = dbr.bindResolve(this, src);
        }
        return o;
    }

    /* JADX WARN: Code restructure failed: missing block: B:28:0x00a7, code lost:
    
        r0 = findDataBoundSetter(r12.getClass(), r0);
     */
    /* JADX WARN: Code restructure failed: missing block: B:29:0x00b5, code lost:
    
        if (r0 != null) goto L43;
     */
    /* JADX WARN: Code restructure failed: missing block: B:31:0x00bb, code lost:
    
        r0 = r0.getParameterTypes();
     */
    /* JADX WARN: Code restructure failed: missing block: B:32:0x00c5, code lost:
    
        if (org.kohsuke.stapler.RequestImpl.$assertionsDisabled != false) goto L47;
     */
    /* JADX WARN: Code restructure failed: missing block: B:34:0x00cc, code lost:
    
        if (r0.length == 1) goto L48;
     */
    /* JADX WARN: Code restructure failed: missing block: B:37:0x00d6, code lost:
    
        throw new java.lang.AssertionError();
     */
    /* JADX WARN: Code restructure failed: missing block: B:42:0x00d7, code lost:
    
        r0.invoke(r12, bindJSON(r0.getGenericParameterTypes()[0], r0[0], r13.get(r0)));
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private <T> T injectSetters(T r12, net.sf.json.JSONObject r13, java.util.Collection<java.lang.String> r14) {
        /*
            Method dump skipped, instructions count: 311
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: org.kohsuke.stapler.RequestImpl.injectSetters(java.lang.Object, net.sf.json.JSONObject, java.util.Collection):java.lang.Object");
    }

    private static void launderITE(InvocationTargetException e) {
        HttpResponse targetException = e.getTargetException();
        if (targetException instanceof Error) {
            Error err = (Error) targetException;
            throw err;
        }
        if (targetException instanceof RuntimeException) {
            RuntimeException rt = (RuntimeException) targetException;
            throw rt;
        }
        if (targetException instanceof HttpResponse) {
            HttpResponse httpResponse = targetException;
            throw HttpResponses.wrap(httpResponse);
        }
        throw new IllegalArgumentException((Throwable) targetException);
    }

    private Method findDataBoundSetter(Class c, String name) {
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("set") && m.getParameterTypes().length == 1 && m.isAnnotationPresent(DataBoundSetter.class)) {
                    String propertyName = Introspector.decapitalize(m.getName().substring(3));
                    if (name.equals(propertyName)) {
                        return m;
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private void invokePostConstruct(SingleLinkedList<MethodRef> methods, Object r) {
        if (methods.isEmpty()) {
            return;
        }
        invokePostConstruct(methods.tail, r);
        try {
            ((MethodRef) methods.head).invoke(r, new Object[0]);
        } catch (IllegalAccessException e) {
            throw ((Error) new IllegalAccessError().initCause(e));
        } catch (InvocationTargetException e2) {
            throw new IllegalArgumentException("Unable to post-construct " + String.valueOf(r), e2);
        }
    }

    private TypePair getPropertyType(Object bean, String name) throws IllegalAccessException, InvocationTargetException {
        Method m;
        try {
            PropertyDescriptor propDescriptor = PropertyUtils.getPropertyDescriptor(bean, name);
            if (propDescriptor != null && (m = propDescriptor.getWriteMethod()) != null) {
                return new TypePair(m.getGenericParameterTypes()[0], m.getParameterTypes()[0]);
            }
        } catch (NoSuchMethodException e) {
        }
        try {
            return new TypePair(this, bean.getClass().getField(name));
        } catch (NoSuchFieldException e2) {
            return null;
        }
    }

    private static void copyProperty(Object bean, String name, Object value) throws IllegalAccessException, InvocationTargetException {
        PropertyDescriptor propDescriptor;
        try {
            propDescriptor = PropertyUtils.getPropertyDescriptor(bean, name);
        } catch (NoSuchMethodException e) {
            propDescriptor = null;
        }
        if (propDescriptor != null && propDescriptor.getWriteMethod() == null) {
            propDescriptor = null;
        }
        if (propDescriptor != null) {
            Converter converter = Stapler.lookupConverter(propDescriptor.getPropertyType());
            if (converter != null) {
                value = converter.convert(propDescriptor.getPropertyType(), value);
            }
            try {
                PropertyUtils.setSimpleProperty(bean, name, value);
                return;
            } catch (NoSuchMethodException e2) {
                throw new NoSuchMethodError(e2.getMessage());
            }
        }
        try {
            Field field = bean.getClass().getField(name);
            Converter converter2 = ConvertUtils.lookup(field.getType());
            if (converter2 != null) {
                value = converter2.convert(field.getType(), value);
            }
            field.set(bean, value);
        } catch (NoSuchFieldException e3) {
        }
    }

    private void parseMultipartFormData() throws IOException, ServletException {
        if (this.parsedFormData != null) {
            return;
        }
        this.parsedFormData = new HashMap();
        this.parsedFormDataFormFields = new HashMap();
        try {
            File tmpDir = Files.createTempDirectory("jenkins-stapler-uploads", new FileAttribute[0]).toFile();
            tmpDir.deleteOnExit();
            JakartaServletDiskFileUpload jakartaServletDiskFileUpload = new JakartaServletDiskFileUpload(DiskFileItemFactory.builder().setFile(tmpDir).get());
            jakartaServletDiskFileUpload.setMaxFileCount(FILEUPLOAD_MAX_FILES);
            jakartaServletDiskFileUpload.setMaxFileSize(FILEUPLOAD_MAX_FILE_SIZE);
            jakartaServletDiskFileUpload.setMaxSize(FILEUPLOAD_MAX_SIZE);
            try {
                for (FileItem fi : jakartaServletDiskFileUpload.parseRequest(this)) {
                    this.parsedFormData.put(fi.getFieldName(), fi);
                    if (fi.isFormField()) {
                        this.parsedFormDataFormFields.put(fi.getFieldName(), fi.getString());
                    }
                }
            } catch (FileUploadByteCountLimitException e) {
                throw new ServletException("File upload field size limit exceeded. Consider setting the Java system property " + RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILE_SIZE to a value greater than " + FILEUPLOAD_MAX_FILE_SIZE + ", or to -1 to disable this limit.", e);
            } catch (FileUploadFileCountLimitException e2) {
                throw new ServletException("File upload field count limit exceeded. Consider setting the Java system property " + RequestImpl.class.getName() + ".FILEUPLOAD_MAX_FILES to a value greater than " + FILEUPLOAD_MAX_FILES + ", or to -1 to disable this limit.", e2);
            } catch (FileUploadSizeException e3) {
                throw new ServletException("File upload total size limit exceeded. Consider setting the Java system property " + RequestImpl.class.getName() + ".FILEUPLOAD_MAX_SIZE to a value greater than " + FILEUPLOAD_MAX_SIZE + ", or to -1 to disable this limit.", e3);
            } catch (FileUploadException e4) {
                throw new ServletException(e4);
            }
        } catch (IOException e5) {
            throw new ServletException("Error creating temporary directory", e5);
        }
    }

    private Map<String, String> getFormDataFormFields() {
        try {
            parseMultipartFormData();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing multipart/form-data.", (Throwable) e);
        }
        return this.parsedFormDataFormFields;
    }

    public JSONObject getSubmittedForm() throws ServletException {
        boolean isSubmission;
        String method = getMethod();
        if (!ALLOWED_HTTP_VERBS_FOR_FORMS.contains(method)) {
            throw HttpResponses.errorWithoutStack(400, "Form submission expected but a " + method + " request was sent");
        }
        if (this.structuredForm == null) {
            String p = null;
            if (isMultipart()) {
                isSubmission = true;
                try {
                    parseMultipartFormData();
                    FileItem item = this.parsedFormData.get("json");
                    if (item != null) {
                        if (item.getContentType() == null && getCharacterEncoding() != null) {
                            try {
                                p = item.getString(Charset.forName(getCharacterEncoding()));
                            } catch (UnsupportedEncodingException uee) {
                                LOGGER.log(Level.WARNING, "Request has unsupported charset, using default for 'json' parameter", (Throwable) uee);
                                p = item.getString();
                            }
                        } else {
                            p = item.getString();
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                p = getParameter("json");
                isSubmission = !getParameterMap().isEmpty();
            }
            if (p == null || p.isEmpty()) {
                try {
                    StaplerResponse2 rsp = Stapler.getCurrentResponse2();
                    if (isSubmission) {
                        rsp.sendError(400, "This page expects a form submission");
                    } else {
                        rsp.sendError(400, "Nothing is submitted");
                    }
                    throw new ServletException("This page expects a form submission but had only " + String.valueOf(getParameterMap()));
                } catch (IOException e2) {
                    throw new ServletException(e2);
                }
            }
            try {
                this.structuredForm = JSONObject.fromObject(p);
            } catch (JSONException e3) {
                throw new ServletException("Failed to parse JSON:" + p, e3);
            }
        }
        return this.structuredForm;
    }

    private boolean isMultipart() {
        String ct = getContentType();
        return ct != null && ct.startsWith("multipart/");
    }

    public FileItem getFileItem2(String name) throws ServletException, IOException {
        FileItem item;
        if (!isMultipart()) {
            return null;
        }
        parseMultipartFormData();
        if (this.parsedFormData == null || (item = this.parsedFormData.get(name)) == null || item.isFormField()) {
            return null;
        }
        return item;
    }

    @Deprecated
    public org.apache.commons.fileupload.FileItem getFileItem(String name) throws ServletException, IOException {
        FileItem fileItem = getFileItem2(name);
        if (fileItem != null) {
            return org.apache.commons.fileupload.FileItem.fromFileUpload2FileItem(fileItem);
        }
        return null;
    }
}
