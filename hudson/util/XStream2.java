package hudson.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.ConverterMatcher;
import com.thoughtworks.xstream.converters.ConverterRegistry;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.SingleValueConverterWrapper;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.DynamicProxyConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.Fields;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.security.AnyTypePermission;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.remoting.ClassFilter;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.xstream.ImmutableListConverter;
import hudson.util.xstream.ImmutableMapConverter;
import hudson.util.xstream.ImmutableSetConverter;
import hudson.util.xstream.ImmutableSortedSetConverter;
import hudson.util.xstream.MapperDelegate;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.xstream.SafeURLConverter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/* loaded from: XStream2.class */
public class XStream2 extends XStream {
    private static final Logger LOGGER = Logger.getLogger(XStream2.class.getName());

    @Restricted({NoExternalUse.class})
    public static final String COLLECTION_UPDATE_LIMIT_PROPERTY_NAME = XStream2.class.getName() + ".collectionUpdateLimit";
    private static final int COLLECTION_UPDATE_LIMIT_DEFAULT_VALUE = 5;
    private RobustReflectionConverter reflectionConverter;
    private final ThreadLocal<Boolean> oldData;

    @CheckForNull
    private final ClassOwnership classOwnership;
    private final Map<String, Class<?>> compatibilityAliases;
    private MapperInjectionPoint mapperInjectionPoint;

    public static HierarchicalStreamDriver getDefaultDriver() {
        return new StaxDriver();
    }

    public XStream2() {
        super(getDefaultDriver());
        this.oldData = new ThreadLocal<>();
        this.compatibilityAliases = new ConcurrentHashMap();
        init();
        this.classOwnership = null;
    }

    public XStream2(HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(hierarchicalStreamDriver);
        this.oldData = new ThreadLocal<>();
        this.compatibilityAliases = new ConcurrentHashMap();
        init();
        this.classOwnership = null;
    }

    public XStream2(ReflectionProvider reflectionProvider, HierarchicalStreamDriver driver, ClassLoaderReference classLoaderReference, Mapper mapper, ConverterLookup converterLookup, ConverterRegistry converterRegistry) {
        super(reflectionProvider, driver, classLoaderReference, mapper, converterLookup, converterRegistry);
        this.oldData = new ThreadLocal<>();
        this.compatibilityAliases = new ConcurrentHashMap();
        init();
        this.classOwnership = null;
    }

    XStream2(ClassOwnership classOwnership) {
        super(getDefaultDriver());
        this.oldData = new ThreadLocal<>();
        this.compatibilityAliases = new ConcurrentHashMap();
        init();
        this.classOwnership = classOwnership;
    }

    public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder) {
        return unmarshal(reader, root, dataHolder, false);
    }

    public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder, boolean nullOut) {
        Object o;
        Jenkins h = Jenkins.getInstanceOrNull();
        if (h != null && h.pluginManager != null && h.pluginManager.uberClassLoader != null) {
            setClassLoader(h.pluginManager.uberClassLoader);
        }
        if (root == null || !nullOut) {
            o = super.unmarshal(reader, root, dataHolder);
        } else {
            Set<String> topLevelFields = new HashSet<>();
            o = super.unmarshal(new 1(this, reader, topLevelFields), root, dataHolder);
            if (o == root && (getConverterLookup().lookupConverterForType(o.getClass()) instanceof RobustReflectionConverter)) {
                getReflectionProvider().visitSerializableFields(o, (name, type, definedIn, value) -> {
                    Object v;
                    if (topLevelFields.contains(name)) {
                        return;
                    }
                    Field f = Fields.find(definedIn, name);
                    if (type.isPrimitive()) {
                        v = ReflectionUtils.getVmDefaultValueForPrimitiveType(type);
                        if (v.equals(value)) {
                            return;
                        }
                    } else if (value == null) {
                        return;
                    } else {
                        v = null;
                    }
                    LOGGER.log(Level.FINE, "JENKINS-21017: nulling out {0} in {1}", new Object[]{f, o});
                    Fields.write(f, o, v);
                });
            }
        }
        if (this.oldData.get() != null) {
            this.oldData.remove();
            if (o instanceof Saveable) {
                OldDataMonitor.report((Saveable) o, "1.106");
            }
        }
        return o;
    }

    protected void setupConverters() {
        super.setupConverters();
        this.reflectionConverter = new RobustReflectionConverter(getMapper(), JVM.newReflectionProvider(), new PluginClassOwnership());
        registerConverter(this.reflectionConverter, -19);
    }

    public void addCriticalField(Class<?> clazz, String field) {
        this.reflectionConverter.addCriticalField(clazz, field);
    }

    static String trimVersion(String version) {
        return version.replaceFirst(" .+$", "");
    }

    private void init() {
        int updateLimit = SystemProperties.getInteger(COLLECTION_UPDATE_LIMIT_PROPERTY_NAME, Integer.valueOf(COLLECTION_UPDATE_LIMIT_DEFAULT_VALUE)).intValue();
        setCollectionUpdateLimit(updateLimit);
        addImmutableType(Result.class, false);
        denyTypes(new Class[]{Void.TYPE, Void.class});
        registerConverter(new RobustCollectionConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new RobustMapConverter(getMapper()), 10);
        registerConverter(new ImmutableMapConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new ImmutableSortedSetConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new ImmutableSetConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new ImmutableListConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new CopyOnWriteMap.Tree.ConverterImpl(getMapper()), 10);
        registerConverter(new DescribableList.ConverterImpl(getMapper()), 10);
        registerConverter(new Label.ConverterImpl(), 10);
        registerConverter(new SafeURLConverter(), 10);
        registerConverter(new AssociatedConverterImpl(this), -10);
        registerConverter(new BlacklistedTypesConverter(), 10000);
        addPermission(AnyTypePermission.ANY);
        registerConverter(new DynamicProxyConverter(this, getMapper(), new ClassLoaderReference(getClassLoader())) { // from class: hudson.util.XStream2.2
            public boolean canConvert(Class type) {
                return type != null && super.canConvert(type);
            }

            public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                throw new ConversionException("<dynamic-proxy> not supported");
            }
        }, 10000);
    }

    protected MapperWrapper wrapMapper(MapperWrapper next) {
        this.mapperInjectionPoint = new MapperInjectionPoint(new CompatibilityMapper(new MapperWrapper(this, next) { // from class: hudson.util.XStream2.3
            public String serializedClass(Class type) {
                if (type != null && ImmutableMap.class.isAssignableFrom(type)) {
                    return super.serializedClass(ImmutableMap.class);
                }
                if (type != null && ImmutableList.class.isAssignableFrom(type)) {
                    return super.serializedClass(ImmutableList.class);
                }
                return super.serializedClass(type);
            }
        }));
        return this.mapperInjectionPoint;
    }

    public Mapper getMapperInjectionPoint() {
        return this.mapperInjectionPoint.getDelegate();
    }

    @Deprecated
    public void toXML(Object obj, OutputStream out) {
        super.toXML(obj, out);
    }

    public void toXMLUTF8(Object obj, OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        toXML(obj, w);
    }

    public void setMapper(Mapper m) {
        this.mapperInjectionPoint.setDelegate(m);
    }

    /* loaded from: XStream2$MapperInjectionPoint.class */
    static final class MapperInjectionPoint extends MapperDelegate {
        MapperInjectionPoint(Mapper wrapped) {
            super(wrapped);
        }

        public Mapper getDelegate() {
            return this.delegate;
        }

        public void setDelegate(Mapper m) {
            this.delegate = m;
        }
    }

    public void addCompatibilityAlias(String oldClassName, Class newClass) {
        this.compatibilityAliases.put(oldClassName, newClass);
    }

    /* loaded from: XStream2$CompatibilityMapper.class */
    private class CompatibilityMapper extends MapperWrapper {
        private CompatibilityMapper(Mapper wrapped) {
            super(wrapped);
        }

        public Class realClass(String elementName) {
            Class s = XStream2.this.compatibilityAliases.get(elementName);
            if (s != null) {
                return s;
            }
            try {
                return super.realClass(elementName);
            } catch (CannotResolveClassException e) {
                if (elementName.indexOf(45) >= 0) {
                    try {
                        Class c = super.realClass(elementName.replace('-', '$'));
                        XStream2.this.oldData.set(Boolean.TRUE);
                        return c;
                    } catch (CannotResolveClassException e2) {
                        throw e;
                    }
                }
                throw e;
            }
        }
    }

    /* loaded from: XStream2$AssociatedConverterImpl.class */
    private static final class AssociatedConverterImpl implements Converter {
        private final XStream xstream;
        private static final ClassValue<Class<? extends ConverterMatcher>> classCache = new ClassValue<Class<? extends ConverterMatcher>>() { // from class: hudson.util.XStream2.AssociatedConverterImpl.1
            @Override // java.lang.ClassValue
            protected /* bridge */ /* synthetic */ Class<? extends ConverterMatcher> computeValue(Class type) {
                return computeValue((Class<?>) type);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // java.lang.ClassValue
            protected Class<? extends ConverterMatcher> computeValue(Class<?> type) {
                return AssociatedConverterImpl.computeConverterClass(type);
            }
        };
        private final ConcurrentHashMap<Class<?>, Converter> cache = new ConcurrentHashMap<>();

        private AssociatedConverterImpl(XStream xstream) {
            this.xstream = xstream;
        }

        @CheckForNull
        private Converter findConverter(@CheckForNull Class<?> t) {
            Converter result;
            if (t == null || (result = this.cache.computeIfAbsent(t, unused -> {
                return computeConverter(t);
            })) == this) {
                return null;
            }
            return result;
        }

        @CheckForNull
        private static Class<? extends ConverterMatcher> computeConverterClass(@NonNull Class<?> t) {
            try {
                ClassLoader classLoader = t.getClassLoader();
                if (classLoader == null) {
                    return null;
                }
                String name = t.getName() + "$ConverterImpl";
                if (classLoader.getResource(name.replace('.', '/') + ".class") == null) {
                    return null;
                }
                return classLoader.loadClass(name).asSubclass(ConverterMatcher.class);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @CheckForNull
        private Converter computeConverter(@NonNull Class<?> t) {
            Class<? extends ConverterMatcher> cl = classCache.get(t);
            if (cl == null) {
                return this;
            }
            try {
                Constructor<?> c = cl.getConstructors()[0];
                Class<?>[] p = c.getParameterTypes();
                Object[] args = new Object[p.length];
                for (int i = 0; i < p.length; i++) {
                    if (p[i] == XStream.class || p[i] == XStream2.class) {
                        args[i] = this.xstream;
                    } else if (p[i] == Mapper.class) {
                        args[i] = this.xstream.getMapper();
                    } else {
                        throw new InstantiationError("Unrecognized constructor parameter: " + String.valueOf(p[i]));
                    }
                }
                SingleValueConverter singleValueConverter = (ConverterMatcher) c.newInstance(args);
                if (singleValueConverter instanceof SingleValueConverter) {
                    return new SingleValueConverterWrapper(singleValueConverter);
                }
                return (Converter) singleValueConverter;
            } catch (IllegalAccessException e) {
                IllegalAccessError x = new IllegalAccessError();
                x.initCause(e);
                throw x;
            } catch (InstantiationException | InvocationTargetException e2) {
                InstantiationError x2 = new InstantiationError();
                x2.initCause(e2);
                throw x2;
            }
        }

        public boolean canConvert(Class type) {
            return findConverter(type) != null;
        }

        @SuppressFBWarnings(value = {"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "TODO needs triage")
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            findConverter(source.getClass()).marshal(source, writer, context);
        }

        @SuppressFBWarnings(value = {"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "TODO needs triage")
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return findConverter(context.getRequiredType()).unmarshal(reader, context);
        }
    }

    /* loaded from: XStream2$PassthruConverter.class */
    public static abstract class PassthruConverter<T> implements Converter {
        private Converter converter;

        protected abstract void callback(T obj, UnmarshallingContext context);

        protected PassthruConverter(XStream2 xstream) {
            this.converter = xstream.reflectionConverter;
        }

        public boolean canConvert(Class type) {
            return false;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            this.converter.marshal(source, writer, context);
        }

        /* JADX WARN: Multi-variable type inference failed */
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Object obj = this.converter.unmarshal(reader, context);
            callback(obj, context);
            return obj;
        }
    }

    /* loaded from: XStream2$PluginClassOwnership.class */
    class PluginClassOwnership implements ClassOwnership {
        private PluginManager pm;

        PluginClassOwnership() {
        }

        public String ownerOf(Class<?> clazz) {
            PluginWrapper p;
            Jenkins j;
            if (XStream2.this.classOwnership != null) {
                return XStream2.this.classOwnership.ownerOf(clazz);
            }
            if (this.pm == null && (j = Jenkins.getInstanceOrNull()) != null) {
                this.pm = j.getPluginManager();
            }
            if (this.pm == null || (p = this.pm.whichPlugin(clazz)) == null) {
                return null;
            }
            return p.getShortName() + "@" + XStream2.trimVersion(p.getVersion());
        }
    }

    /* loaded from: XStream2$BlacklistedTypesConverter.class */
    private static class BlacklistedTypesConverter implements Converter {
        private BlacklistedTypesConverter() {
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            throw new UnsupportedOperationException("Refusing to marshal " + source.getClass().getName() + " for security reasons; see https://www.jenkins.io/redirect/class-filter/");
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            throw new ConversionException("Refusing to unmarshal " + reader.getNodeName() + " for security reasons; see https://www.jenkins.io/redirect/class-filter/");
        }

        public boolean canConvert(Class type) {
            if (type == null) {
                return false;
            }
            String name = type.getName();
            return ClassFilter.DEFAULT.isBlacklisted(name) || ClassFilter.DEFAULT.isBlacklisted(type);
        }
    }
}
