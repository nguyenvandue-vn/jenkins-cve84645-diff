package hudson.util;

import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ObjectAccessException;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.util.Primitives;
import com.thoughtworks.xstream.core.util.SerializationMembers;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.InputManipulationException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.PersistenceRoot;
import hudson.model.Saveable;
import hudson.security.ACL;
import hudson.util.CopyOnWriteList;
import hudson.util.DescribableList;
import hudson.util.PersistedList;
import hudson.util.XStream2;
import hudson.util.XStream2.PluginClassOwnership;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.xstream.CriticalXStreamException;
import net.jcip.annotations.GuardedBy;
import org.acegisecurity.Authentication;
import org.jvnet.tiger_types.Types;

/* loaded from: RobustReflectionConverter.class */
public class RobustReflectionConverter implements Converter {
    static boolean DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK;
    static boolean TRANSIENT_FIELD_STRICT_MODE;
    static boolean RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS;
    private static boolean RECORD_FAILURES_FOR_ADMINS;
    static final Set<String> SAFE_TYPES_WITH_OBJECT_FIELDS;
    static boolean ALLOW_ALL_OBJECT_FIELDS;
    protected final ReflectionProvider reflectionProvider;
    protected final Mapper mapper;
    protected transient SerializationMembers serializationMethodInvoker;
    private transient ReflectionProvider pureJavaReflectionProvider;

    @NonNull
    private final XStream2.ClassOwnership classOwnership;
    private final ReadWriteLock criticalFieldsLock;

    @GuardedBy("criticalFieldsLock")
    private final Map<String, Set<String>> criticalFields;
    private ConverterLookup converterLookup;
    private static final Logger LOGGER;
    static final /* synthetic */ boolean $assertionsDisabled;

    static {
        $assertionsDisabled = !RobustReflectionConverter.class.desiredAssertionStatus();
        DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK", false);
        TRANSIENT_FIELD_STRICT_MODE = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".TRANSIENT_FIELD_STRICT_MODE", false);
        RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".recordFailuresForAllAuthentications", false);
        RECORD_FAILURES_FOR_ADMINS = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".recordFailuresForAdmins", false);
        SAFE_TYPES_WITH_OBJECT_FIELDS = new HashSet();
        ALLOW_ALL_OBJECT_FIELDS = SystemProperties.getBoolean(RobustReflectionConverter.class.getName() + ".ALLOW_ALL_OBJECT_FIELDS", false);
        String classNames = SystemProperties.getString(RobustReflectionConverter.class.getName() + ".SAFE_TYPES_WITH_OBJECT_FIELDS");
        if (classNames != null) {
            for (String className : classNames.split(",")) {
                SAFE_TYPES_WITH_OBJECT_FIELDS.add(className.trim());
            }
        }
        SAFE_TYPES_WITH_OBJECT_FIELDS.add("org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue");
        LOGGER = Logger.getLogger(RobustReflectionConverter.class.getName());
    }

    void setConverterLookup(ConverterLookup converterLookup) {
        this.converterLookup = converterLookup;
    }

    public RobustReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        this(mapper, reflectionProvider, new XStream2().new PluginClassOwnership());
    }

    RobustReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider, XStream2.ClassOwnership classOwnership) {
        this.criticalFieldsLock = new ReentrantReadWriteLock();
        this.criticalFields = new HashMap();
        this.mapper = mapper;
        this.reflectionProvider = reflectionProvider;
        if (!$assertionsDisabled && classOwnership == null) {
            throw new AssertionError();
        }
        this.classOwnership = classOwnership;
        this.serializationMethodInvoker = new SerializationMembers();
    }

    void addCriticalField(Class<?> clazz, String field) {
        this.criticalFieldsLock.writeLock().lock();
        try {
            if (!this.criticalFields.containsKey(field)) {
                this.criticalFields.put(field, new HashSet());
            }
            this.criticalFields.get(field).add(clazz.getName());
        } finally {
            this.criticalFieldsLock.writeLock().unlock();
        }
    }

    private boolean hasCriticalField(Class<?> clazz, String field) {
        this.criticalFieldsLock.readLock().lock();
        try {
            Set<String> classesWithField = this.criticalFields.get(field);
            if (classesWithField == null) {
                return false;
            }
            if (classesWithField.contains(clazz.getName())) {
                this.criticalFieldsLock.readLock().unlock();
                return true;
            }
            this.criticalFieldsLock.readLock().unlock();
            return false;
        } finally {
            this.criticalFieldsLock.readLock().unlock();
        }
    }

    public boolean canConvert(Class type) {
        return true;
    }

    public void marshal(Object original, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        Object source = this.serializationMethodInvoker.callWriteReplace(original);
        if (source.getClass() != original.getClass()) {
            writer.addAttribute(this.mapper.aliasForAttribute("resolves-to"), this.mapper.serializedClass(source.getClass()));
        }
        OwnerContext oc = OwnerContext.find(context);
        oc.startVisiting(writer, this.classOwnership.ownerOf(original.getClass()));
        try {
            doMarshal(source, writer, context);
            oc.stopVisiting();
        } catch (Throwable th) {
            oc.stopVisiting();
            throw th;
        }
    }

    /* loaded from: RobustReflectionConverter$OwnerContext.class */
    private static class OwnerContext extends LinkedList<String> {
        private OwnerContext() {
        }

        static OwnerContext find(MarshallingContext context) {
            OwnerContext c = (OwnerContext) context.get(OwnerContext.class);
            if (c == null) {
                c = new OwnerContext();
                context.put(OwnerContext.class, c);
            }
            return c;
        }

        private void startVisiting(HierarchicalStreamWriter writer, String owner) {
            if (owner != null) {
                boolean redundant = false;
                Iterator it = iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    String parentOwner = (String) it.next();
                    if (parentOwner != null) {
                        redundant = parentOwner.equals(owner);
                        break;
                    }
                }
                if (!redundant) {
                    writer.addAttribute("plugin", owner);
                }
            }
            addFirst(owner);
        }

        private void stopVisiting() {
            removeFirst();
        }
    }

    protected void doMarshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Set seenFields = new HashSet();
        final Set seenAsAttributes = new HashSet();
        this.reflectionProvider.visitSerializableFields(source, (fieldName, type, definedIn, value) -> {
            String str;
            SingleValueConverter converter = this.mapper.getConverterFromItemType(fieldName, type, definedIn);
            if (converter == null) {
                converter = this.mapper.getConverterFromItemType(fieldName, type);
            }
            if (converter == null) {
                converter = this.mapper.getConverterFromItemType(type);
            }
            if (converter != null) {
                if (value != null && (str = converter.toString(value)) != null) {
                    writer.addAttribute(this.mapper.aliasForAttribute(fieldName), str);
                }
                seenAsAttributes.add(fieldName);
            }
        });
        this.reflectionProvider.visitSerializableFields(source, new ReflectionProvider.Visitor() { // from class: hudson.util.RobustReflectionConverter.1
            public void visit(String fieldName2, Class fieldType, Class definedIn2, Object newObj) {
                if (!seenAsAttributes.contains(fieldName2) && newObj != null) {
                    Mapper.ImplicitCollectionMapping mapping = RobustReflectionConverter.this.mapper.getImplicitCollectionDefForFieldName(source.getClass(), fieldName2);
                    if (mapping != null) {
                        if (mapping.getItemFieldName() != null) {
                            Collection list = (Collection) newObj;
                            for (Object obj : list) {
                                writeField(fieldName2, mapping.getItemFieldName(), mapping.getItemType(), definedIn2, obj);
                            }
                            return;
                        }
                        context.convertAnother(newObj);
                        return;
                    }
                    writeField(fieldName2, fieldName2, fieldType, definedIn2, newObj);
                    seenFields.add(fieldName2);
                }
            }

            private void writeField(String fieldName2, String aliasName, Class fieldType, Class definedIn2, Object newObj) {
                try {
                    if (!RobustReflectionConverter.this.mapper.shouldSerializeMember(definedIn2, aliasName)) {
                        return;
                    }
                    ExtendedHierarchicalStreamWriterHelper.startNode(writer, RobustReflectionConverter.this.mapper.serializedMember(definedIn2, aliasName), fieldType);
                    Class actualType = newObj.getClass();
                    Class defaultType = RobustReflectionConverter.this.mapper.defaultImplementationOf(fieldType);
                    if (!actualType.equals(defaultType)) {
                        String serializedClassName = RobustReflectionConverter.this.mapper.serializedClass(actualType);
                        if (!serializedClassName.equals(RobustReflectionConverter.this.mapper.serializedClass(defaultType))) {
                            writer.addAttribute(RobustReflectionConverter.this.mapper.aliasForSystemAttribute("class"), serializedClassName);
                        }
                    }
                    if (seenFields.contains(aliasName)) {
                        writer.addAttribute(RobustReflectionConverter.this.mapper.aliasForAttribute("defined-in"), RobustReflectionConverter.this.mapper.serializedClass(definedIn2));
                    }
                    Field field = RobustReflectionConverter.this.reflectionProvider.getField(definedIn2, fieldName2);
                    RobustReflectionConverter.this.marshallField(context, newObj, field);
                    writer.endNode();
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed to serialize " + definedIn2.getName() + "#" + fieldName2 + " for " + String.valueOf(source.getClass()), e);
                }
            }
        });
    }

    protected void marshallField(final MarshallingContext context, Object newObj, Field field) {
        Converter converter = this.mapper.getLocalConverter(field.getDeclaringClass(), field.getName());
        context.convertAnother(newObj, converter);
    }

    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        Object result = instantiateNewInstance(reader, context);
        return this.serializationMethodInvoker.callReadResolve(doUnmarshal(result, reader, context));
    }

    public Object doUnmarshal(final Object result, final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        Object value;
        SeenFields seenFields = new SeenFields();
        Iterator it = reader.getAttributeNames();
        if ((result instanceof Saveable) && context.get("Saveable") == null) {
            context.put("Saveable", result);
        }
        while (it.hasNext()) {
            String attrAlias = (String) it.next();
            String attrName = this.mapper.attributeForAlias(attrAlias);
            Class classDefiningField = determineWhichClassDefinesField(reader);
            if (fieldDefinedInClass(result, attrName)) {
                Field field = this.reflectionProvider.getField(result.getClass(), attrName);
                SingleValueConverter converter = this.mapper.getConverterFromAttribute(field.getDeclaringClass(), attrName, field.getType());
                Class type = field.getType();
                if (converter == null) {
                    converter = this.mapper.getConverterFromItemType(type);
                }
                if (converter != null) {
                    Object value2 = converter.fromString(reader.getAttribute(attrAlias));
                    if (type.isPrimitive()) {
                        type = Primitives.box(type);
                    }
                    if (value2 != null && !type.isAssignableFrom(value2.getClass())) {
                        throw new ConversionException("Cannot convert type " + value2.getClass().getName() + " to type " + type.getName());
                    }
                    this.reflectionProvider.writeField(result, attrName, value2, classDefiningField);
                    seenFields.add(classDefiningField, attrName);
                } else {
                    continue;
                }
            }
        }
        Map<String, Collection<Object>> implicitCollectionsForCurrentObject = new HashMap<>();
        Map<String, Class<?>> implicitCollectionElementTypesForCurrentObject = new HashMap<>();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            boolean critical = false;
            try {
                String fieldName = this.mapper.realMember(result.getClass(), reader.getNodeName());
                Class<?> concrete = result.getClass();
                while (true) {
                    if (concrete == null) {
                        break;
                    }
                    if (!hasCriticalField(concrete, fieldName)) {
                        concrete = concrete.getSuperclass();
                    } else {
                        critical = true;
                        break;
                    }
                }
                boolean implicitCollectionHasSameName = this.mapper.getImplicitCollectionDefForFieldName(result.getClass(), reader.getNodeName()) != null;
                Class classDefiningField2 = determineWhichClassDefinesField(reader);
                boolean fieldExistsInClass = !implicitCollectionHasSameName && fieldDefinedInClass(result, fieldName);
                Class type2 = determineType(reader, fieldExistsInClass, result, fieldName, classDefiningField2);
                if (fieldExistsInClass) {
                    Field field2 = this.reflectionProvider.getField(result.getClass(), fieldName);
                    if (PersistenceRoot.class.isAssignableFrom(type2) && !isSafePersistenceRootReference(reader)) {
                        String msg = "Refusing to unmarshal PersistenceRoot subtype '" + type2.getName() + "' into field '" + fieldName + "' in '" + result.getClass().getName() + "'. PersistenceRoot objects are document roots and must not appear as nested field values.";
                        LOGGER.log(Level.WARNING, msg);
                        throw new CriticalXStreamException(new XStreamException(msg));
                    }
                    value = unmarshalField(context, result, type2, field2);
                    Class definedType = this.reflectionProvider.getFieldType(result, fieldName, classDefiningField2);
                    if (!definedType.isPrimitive()) {
                        type2 = definedType;
                    }
                } else {
                    value = context.convertAnother(result, type2);
                }
                if (value != null && !type2.isAssignableFrom(value.getClass())) {
                    LOGGER.warning("Cannot convert type " + value.getClass().getName() + " to type " + type2.getName());
                } else if (fieldExistsInClass) {
                    this.reflectionProvider.writeField(result, fieldName, value, classDefiningField2);
                    seenFields.add(classDefiningField2, fieldName);
                } else {
                    writeValueToImplicitCollection(reader, context, value, implicitCollectionsForCurrentObject, implicitCollectionElementTypesForCurrentObject, result, fieldName);
                }
            } catch (CriticalXStreamException e) {
                throw e;
            } catch (XStreamException e2) {
                if (critical) {
                    throw new CriticalXStreamException(e2);
                }
                addErrorInContext(context, e2);
            } catch (LinkageError e3) {
                if (critical) {
                    throw e3;
                }
                addErrorInContext(context, e3);
            } catch (InputManipulationException e4) {
                LOGGER.warning("DoS detected and prevented. If the heuristic was too aggressive, you can customize the behavior by setting the hudson.util.XStream2.collectionUpdateLimit system property. See https://www.jenkins.io/redirect/xstream-dos-prevention for more information.");
                throw new CriticalXStreamException(e4);
            }
            reader.moveUp();
        }
        if (shouldReportUnloadableDataForCurrentUser() && context.get("ReadError") != null && context.get("Saveable") == result) {
            try {
                OldDataMonitor.report((Saveable) result, (ArrayList) context.get("ReadError"));
            } catch (Throwable t) {
                StringBuilder message = new StringBuilder("There was a problem reporting unmarshalling field errors");
                Level level = Level.WARNING;
                if ((t instanceof IllegalStateException) && t.getMessage().contains("Expected 1 instance of " + OldDataMonitor.class.getName())) {
                    message.append(". Make sure this code is executed after InitMilestone.EXTENSIONS_AUGMENTED stage, for example in Plugin#postInitialize instead of Plugin#start");
                    level = Level.INFO;
                }
                LOGGER.log(level, message.toString(), t);
            }
            context.put("ReadError", (Object) null);
        }
        return result;
    }

    private static boolean shouldReportUnloadableDataForCurrentUser() {
        if (RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS) {
            return true;
        }
        Authentication authentication = Jenkins.getAuthentication();
        if (authentication.equals(ACL.SYSTEM)) {
            return true;
        }
        return RECORD_FAILURES_FOR_ADMINS && Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    public static void addErrorInContext(UnmarshallingContext context, Throwable e) {
        LOGGER.log(Level.FINE, "Failed to load", e);
        ArrayList<Throwable> list = (ArrayList) context.get("ReadError");
        if (list == null) {
            ArrayList<Throwable> arrayList = new ArrayList<>();
            list = arrayList;
            context.put("ReadError", arrayList);
        }
        list.add(e);
    }

    private boolean isSafePersistenceRootReference(HierarchicalStreamReader reader) {
        String referenceAttr = reader.getAttribute(this.mapper.aliasForSystemAttribute("reference"));
        if (referenceAttr != null) {
            return true;
        }
        String classAttr = reader.getAttribute(this.mapper.aliasForAttribute("class"));
        if (classAttr != null) {
            if (this.converterLookup != null) {
                try {
                    Class<?> resolvedType = this.mapper.realClass(classAttr);
                    if (this.converterLookup.lookupConverterForType(resolvedType) instanceof SingleValueConverter) {
                        return true;
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }
        String resolvesToAttr = reader.getAttribute(this.mapper.aliasForAttribute("resolves-to"));
        if (resolvesToAttr == null) {
            return false;
        }
        try {
            Class<?> replacerType = this.mapper.realClass(resolvesToAttr);
            return !PersistenceRoot.class.isAssignableFrom(replacerType);
        } catch (Exception e2) {
            return false;
        }
    }

    private boolean fieldDefinedInClass(Object result, String attrName) {
        Field field = this.reflectionProvider.getFieldOrNull(result.getClass(), attrName);
        if (field == null) {
            return false;
        }
        if (!Modifier.isTransient(field.getModifiers())) {
            return true;
        }
        if (DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK || !Arrays.stream(field.getAnnotations()).anyMatch(a -> {
            return "XStreamNotDeserializable".equals(a.annotationType().getSimpleName());
        })) {
            return !TRANSIENT_FIELD_STRICT_MODE || Arrays.stream(field.getAnnotations()).anyMatch(a2 -> {
                return "XStreamDeserializable".equals(a2.annotationType().getSimpleName());
            });
        }
        return false;
    }

    protected Object unmarshalField(final UnmarshallingContext context, final Object result, Class type, Field field) {
        if (!ALLOW_ALL_OBJECT_FIELDS && field.getType().equals(Object.class) && Arrays.stream(field.getAnnotations()).noneMatch(a -> {
            return "XstreamSafeObjectField".equals(a.annotationType().getSimpleName());
        }) && !SAFE_TYPES_WITH_OBJECT_FIELDS.contains(field.getDeclaringClass().getName())) {
            String declaringClassName = field.getDeclaringClass().getName();
            String msg = "Refusing to unmarshal type '" + type.getName() + "' to Object typed field '" + field.getName() + "' in '" + declaringClassName + "'. Update the plugin defining this class to a compatible release, set the Java system property '" + RobustReflectionConverter.class.getName() + ".SAFE_TYPES_WITH_OBJECT_FIELDS' to add known safe types (add '" + declaringClassName + "' to the comma-separated list for this occurrence), or disable this protection entirely by setting the Java system property '" + RobustReflectionConverter.class.getName() + ".ALLOW_ALL_OBJECT_FIELDS' to 'true'. Learn more: https://www.jenkins.io/redirect/safe-object-field/";
            LOGGER.log(Level.WARNING, msg);
            throw new CriticalXStreamException(new XStreamException(msg));
        }
        RobustCollectionConverter localConverter = this.mapper.getLocalConverter(field.getDeclaringClass(), field.getName());
        if (localConverter == null) {
            if (new RobustCollectionConverter(this.mapper, this.reflectionProvider).canConvert(type)) {
                localConverter = new RobustCollectionConverter(this.mapper, this.reflectionProvider, field.getGenericType());
            } else if (new RobustMapConverter(this.mapper).canConvert(type)) {
                localConverter = new RobustMapConverter(this.mapper, field.getGenericType());
            } else if (DescribableList.ConverterImpl.canConvertRobust(type)) {
                Class<?> elementType = extractElementType(field.getGenericType(), DescribableList.class);
                localConverter = new DescribableList.ConverterImpl(this.mapper, elementType);
            } else if (PersistedList.ConverterImpl.canConvertRobust(type)) {
                Class<?> elementType2 = extractElementType(field.getGenericType(), PersistedList.class);
                localConverter = new PersistedList.ConverterImpl(this.mapper, elementType2);
            } else if (CopyOnWriteList.ConverterImpl.canConvertRobust(type)) {
                Class<?> elementType3 = extractElementType(field.getGenericType(), CopyOnWriteList.class);
                localConverter = new CopyOnWriteList.ConverterImpl(this.mapper, elementType3);
            }
        }
        return context.convertAnother(result, type, localConverter);
    }

    private Class<?> extractElementType(Type genericType, Class<?> listClass) {
        if (genericType != null && listClass.isAssignableFrom(Types.erasure(genericType))) {
            Type baseType = Types.getBaseClass(genericType, listClass);
            Type typeArg = Types.getTypeArgument(baseType, 0, Object.class);
            return Types.erasure(typeArg);
        }
        return null;
    }

    private void writeValueToImplicitCollection(HierarchicalStreamReader reader, UnmarshallingContext context, Object value, Map<String, Collection<Object>> implicitCollections, Map<String, Class<?>> implicitCollectionElementTypes, Object result, String itemFieldName) {
        String fieldName = this.mapper.getFieldNameForItemTypeAndName(context.getRequiredType(), value.getClass(), itemFieldName);
        if (fieldName != null) {
            Collection collection = implicitCollections.get(fieldName);
            if (collection == null) {
                Field field = this.reflectionProvider.getField(result.getClass(), fieldName);
                Class<?> fieldType = this.mapper.defaultImplementationOf(field.getType());
                if (!Collection.class.isAssignableFrom(fieldType)) {
                    throw new ObjectAccessException("Field " + fieldName + " of " + result.getClass().getName() + " is configured for an implicit Collection, but field is of type " + fieldType.getName());
                }
                if (this.pureJavaReflectionProvider == null) {
                    this.pureJavaReflectionProvider = new PureJavaReflectionProvider();
                }
                collection = (Collection) this.pureJavaReflectionProvider.newInstance(fieldType);
                this.reflectionProvider.writeField(result, fieldName, collection, (Class) null);
                implicitCollections.put(fieldName, collection);
                Type fieldGenericType = field.getGenericType();
                Type elementGenericType = Types.getTypeArgument(Types.getBaseClass(fieldGenericType, Collection.class), 0, Object.class);
                implicitCollectionElementTypes.put(fieldName, Types.erasure(elementGenericType));
            }
            Class<?> elementType = implicitCollectionElementTypes.getOrDefault(fieldName, Object.class);
            if (!elementType.isInstance(value)) {
                ConversionException exception = new ConversionException("Invalid element type for implicit collection for field: " + fieldName);
                exception.add("required-type", elementType.getName());
                exception.add("class", value.getClass().getName());
                exception.add("converter-type", getClass().getName());
                reader.appendErrors(exception);
                throw exception;
            }
            collection.add(value);
        }
    }

    private Class determineWhichClassDefinesField(HierarchicalStreamReader reader) {
        String definedIn = reader.getAttribute(this.mapper.aliasForAttribute("defined-in"));
        if (definedIn == null) {
            return null;
        }
        return this.mapper.realClass(definedIn);
    }

    protected Object instantiateNewInstance(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String readResolveValue = reader.getAttribute(this.mapper.aliasForAttribute("resolves-to"));
        Class type = readResolveValue != null ? this.mapper.realClass(readResolveValue) : context.getRequiredType();
        Object currentObject = context.currentObject();
        if (currentObject != null && type.isInstance(currentObject)) {
            return currentObject;
        }
        return this.reflectionProvider.newInstance(type);
    }

    /* loaded from: RobustReflectionConverter$SeenFields.class */
    private static class SeenFields {
        private Set seen = new HashSet();

        private SeenFields() {
        }

        public void add(Class definedInCls, String fieldName) {
            String uniqueKey = fieldName;
            if (definedInCls != null) {
                uniqueKey = uniqueKey + " [" + definedInCls.getName() + "]";
            }
            if (this.seen.contains(uniqueKey)) {
                throw new DuplicateFieldException(uniqueKey);
            }
            this.seen.add(uniqueKey);
        }
    }

    private Class determineType(HierarchicalStreamReader reader, boolean validField, Object result, String fieldName, Class definedInCls) {
        String classAttribute = reader.getAttribute(this.mapper.aliasForAttribute("class"));
        if (classAttribute != null) {
            Class specifiedType = this.mapper.realClass(classAttribute);
            Class fieldType = this.reflectionProvider.getFieldType(result, fieldName, definedInCls);
            if (fieldType.isAssignableFrom(specifiedType)) {
                return specifiedType;
            }
        }
        if (!validField) {
            Class itemType = this.mapper.getItemTypeForItemFieldName(result.getClass(), fieldName);
            if (itemType != null) {
                return itemType;
            }
            return this.mapper.realClass(reader.getNodeName());
        }
        Class fieldType2 = this.reflectionProvider.getFieldType(result, fieldName, definedInCls);
        return this.mapper.defaultImplementationOf(fieldType2);
    }

    private Object readResolve() {
        this.serializationMethodInvoker = new SerializationMembers();
        return this;
    }

    /* loaded from: RobustReflectionConverter$DuplicateFieldException.class */
    public static class DuplicateFieldException extends ConversionException {
        public DuplicateFieldException(String msg) {
            super(msg);
            add("duplicate-field", msg);
        }
    }
}
