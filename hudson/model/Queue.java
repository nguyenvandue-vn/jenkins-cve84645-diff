package hudson.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.infradna.tool.bridge_method_injector.BridgeMethodsAdded;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.cli.declarative.CLIResolver;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Node;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.Executables;
import hudson.model.queue.FoldableAction;
import hudson.model.queue.FutureImpl;
import hudson.model.queue.MappingWorksheet;
import hudson.model.queue.QueueListener;
import hudson.model.queue.QueueSorter;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.ScheduleResult;
import hudson.model.queue.SubTask;
import hudson.model.queue.WorkUnit;
import hudson.model.queue.WorkUnitContext;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.tasks.Maven;
import hudson.triggers.SafeTimerTask;
import hudson.util.ConsistentHash;
import hudson.util.Futures;
import hudson.util.Iterators;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.runtime.SwitchBootstraps;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.console.WithConsoleUrl;
import jenkins.model.FullyNamed;
import jenkins.model.FullyNamedModelObject;
import jenkins.model.Jenkins;
import jenkins.model.queue.AsynchronousExecution;
import jenkins.model.queue.CompositeCauseOfBlockage;
import jenkins.model.queue.QueueIdStrategy;
import jenkins.model.queue.QueueItem;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorProvider;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.util.AtmostOneTaskExecutor;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.ThrowingCallable;
import jenkins.util.ThrowingRunnable;
import jenkins.util.Timer;
import net.jcip.annotations.GuardedBy;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CancelRequestHandlingException;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@ExportedBean
@BridgeMethodsAdded
/* loaded from: Queue.class */
public class Queue extends ResourceController implements Saveable {
    private volatile transient LoadBalancer loadBalancer;
    private volatile transient QueueSorter sorter;
    private static final ConsistentHash.Hash<Node> NODE_HASH = (v0) -> {
        return v0.getNodeName();
    };
    private static final Logger LOGGER = Logger.getLogger(Queue.class.getName());
    public static final XStream XSTREAM = new XStream2();
    private final Set<WaitingItem> waitingList = new TreeSet();
    private final ItemList<BlockedItem> blockedProjects = new ItemList<>();
    private final ItemList<BuildableItem> buildables = new ItemList<>();
    private final ItemList<BuildableItem> pendings = new ItemList<>();
    private volatile transient Snapshot snapshot = new Snapshot(this.waitingList, this.blockedProjects, this.buildables, this.pendings);
    private final Cache<Long, LeftItem> leftItems = CacheBuilder.newBuilder().expireAfterWrite(300, TimeUnit.SECONDS).build();
    private final transient AtmostOneTaskExecutor<Void> maintainerThread = new AtmostOneTaskExecutor<>(new Callable<Void>() { // from class: hudson.model.Queue.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // java.util.concurrent.Callable
        public Void call() throws Exception {
            Queue.this.maintain();
            return null;
        }

        public String toString() {
            return "Periodic Jenkins queue maintenance";
        }
    });
    private final transient ReentrantLock lock = new ReentrantLock();
    private final transient Condition condition = this.lock.newCondition();

    /* loaded from: Queue$JobOffer.class */
    public static class JobOffer extends MappingWorksheet.ExecutorSlot {
        public final Executor executor;
        private WorkUnit workUnit;
        static final /* synthetic */ boolean $assertionsDisabled;

        static {
            $assertionsDisabled = !Queue.class.desiredAssertionStatus();
        }

        private JobOffer(Executor executor) {
            this.executor = executor;
        }

        protected void set(WorkUnit p) {
            if (!$assertionsDisabled && this.workUnit != null) {
                throw new AssertionError();
            }
            this.workUnit = p;
            if (!$assertionsDisabled && !this.executor.isParking()) {
                throw new AssertionError();
            }
            this.executor.start(this.workUnit);
        }

        public Executor getExecutor() {
            return this.executor;
        }

        @Deprecated
        public boolean canTake(BuildableItem item) {
            return getCauseOfBlockage(item) == null;
        }

        @CheckForNull
        public CauseOfBlockage getCauseOfBlockage(BuildableItem item) {
            CauseOfBlockage reason;
            Node node = getNode();
            if (node == null) {
                return CauseOfBlockage.fromMessage(Messages._Queue_node_has_been_removed_from_configuration(this.executor.getOwner().getDisplayName()));
            }
            CauseOfBlockage reason2 = node.canTake(item);
            if (reason2 != null) {
                return reason2;
            }
            Iterator it = QueueTaskDispatcher.all().iterator();
            while (it.hasNext()) {
                QueueTaskDispatcher d = (QueueTaskDispatcher) it.next();
                try {
                    reason = d.canTake(node, item);
                } catch (Throwable t) {
                    Queue.LOGGER.log(Level.WARNING, t, () -> {
                        return String.format("Exception evaluating if the node '%s' can take the task '%s'", node.getNodeName(), item.task.getName());
                    });
                    reason = CauseOfBlockage.fromMessage(Messages._Queue_ExceptionCanTake());
                }
                if (reason != null) {
                    return reason;
                }
            }
            if (this.workUnit != null) {
                return CauseOfBlockage.fromMessage(Messages._Queue_executor_slot_already_in_use());
            }
            if (this.executor.getOwner().isOffline()) {
                return new CauseOfBlockage.BecauseNodeIsOffline(node);
            }
            if (!this.executor.getOwner().isAcceptingTasks()) {
                return new CauseOfBlockage.BecauseNodeIsNotAcceptingTasks(node);
            }
            return null;
        }

        public boolean isAvailable() {
            return this.workUnit == null && !this.executor.getOwner().isOffline() && this.executor.getOwner().isAcceptingTasks();
        }

        @CheckForNull
        public Node getNode() {
            return this.executor.getOwner().getNode();
        }

        public boolean isNotExclusive() {
            return getNode().getMode() == Node.Mode.NORMAL;
        }

        public String toString() {
            return String.format("JobOffer[%s #%d]", this.executor.getOwner().getName(), Integer.valueOf(this.executor.getNumber()));
        }
    }

    public Queue(@NonNull LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer.sanitize();
        new MaintainTask(this).periodic();
    }

    public LoadBalancer getLoadBalancer() {
        return this.loadBalancer;
    }

    public void setLoadBalancer(@NonNull LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer.sanitize();
    }

    public QueueSorter getSorter() {
        return this.sorter;
    }

    public void setSorter(QueueSorter sorter) {
        this.sorter = sorter;
    }

    @Restricted({Beta.class})
    /* loaded from: Queue$State.class */
    public static final class State {
        public List<Item> items = new ArrayList();
        public Map<String, Object> properties = new HashMap();

        private Object readResolve() {
            if (this.items == null) {
                this.items = new ArrayList();
            }
            if (this.properties == null) {
                this.properties = new HashMap();
            }
            return this;
        }
    }

    /* JADX WARN: Finally extract failed */
    public void load() {
        List items;
        State state;
        this.lock.lock();
        try {
            try {
                try {
                    this.waitingList.clear();
                    this.blockedProjects.clear();
                    this.buildables.clear();
                    this.pendings.clear();
                    File queueFile = getXMLQueueFile();
                    if (Files.exists(queueFile.toPath(), new LinkOption[0])) {
                        Object unmarshaledObj = new XmlFile(XSTREAM, queueFile).read();
                        if (unmarshaledObj instanceof State) {
                            state = (State) unmarshaledObj;
                            items = state.items;
                        } else {
                            items = (List) unmarshaledObj;
                            state = new State();
                            state.items.addAll(items);
                        }
                        QueueIdStrategy.get().load(state);
                        for (Object o : items) {
                            if (o instanceof Task) {
                                schedule((Task) o, 0);
                            } else if (o instanceof Item) {
                                Item item = (Item) o;
                                if (item.task != null) {
                                    Objects.requireNonNull(item);
                                    switch ((int) SwitchBootstraps.typeSwitch(MethodHandles.lookup(), "typeSwitch", MethodType.methodType(Integer.TYPE, Object.class, Integer.TYPE), WaitingItem.class, BlockedItem.class, BuildableItem.class).dynamicInvoker().invoke(item, 0) /* invoke-custom */) {
                                        case Maven.MavenInstallation.MAVEN_20 /* 0 */:
                                            item.enter(this);
                                            break;
                                        case Maven.MavenInstallation.MAVEN_21 /* 1 */:
                                            item.enter(this);
                                            break;
                                        case Maven.MavenInstallation.MAVEN_30 /* 2 */:
                                            item.enter(this);
                                            break;
                                        default:
                                            throw new IllegalStateException("Unknown item type! " + String.valueOf(item));
                                    }
                                }
                            }
                        }
                        File bk = new File(queueFile.getPath() + ".bak");
                        Files.move(queueFile.toPath(), bk.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    updateSnapshot();
                } catch (IOException | InvalidPathException e) {
                    LOGGER.log(Level.WARNING, "Failed to load the queue file " + String.valueOf(getXMLQueueFile()), (Throwable) e);
                    updateSnapshot();
                }
            } finally {
                this.lock.unlock();
            }
        } catch (Throwable th) {
            updateSnapshot();
            throw th;
        }
    }

    public void save() {
        if (BulkChange.contains(this) || Jenkins.getInstanceOrNull() == null) {
            return;
        }
        XmlFile queueFile = new XmlFile(XSTREAM, getXMLQueueFile());
        this.lock.lock();
        try {
            State state = new State();
            QueueIdStrategy.get().persist(state);
            for (Item item : getItems()) {
                if (!(item.task instanceof TransientTask)) {
                    state.items.add(item);
                }
            }
            try {
                queueFile.write(state);
            } catch (IOException e) {
                LOGGER.log(e instanceof ClosedByInterruptException ? Level.FINE : Level.WARNING, "Failed to write out the queue file " + String.valueOf(getXMLQueueFile()), (Throwable) e);
            }
            SaveableListener.fireOnChange(this, queueFile);
        } finally {
            this.lock.unlock();
        }
    }

    public void clear() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        this.lock.lock();
        try {
            try {
                Iterator it = new ArrayList(this.waitingList).iterator();
                while (it.hasNext()) {
                    WaitingItem i = (WaitingItem) it.next();
                    i.cancel(this);
                }
                this.blockedProjects.cancelAll();
                this.pendings.cancelAll();
                this.buildables.cancelAll();
                updateSnapshot();
                m44scheduleMaintenance();
            } catch (Throwable th) {
                updateSnapshot();
                throw th;
            }
        } finally {
            this.lock.unlock();
        }
    }

    File getXMLQueueFile() {
        String id = SystemProperties.getString(Queue.class.getName() + ".id");
        if (id != null) {
            return new File(Jenkins.get().getRootDir(), "queue/" + id + ".xml");
        }
        return new File(Jenkins.get().getRootDir(), "queue.xml");
    }

    @Deprecated
    public boolean add(AbstractProject p) {
        return schedule(p) != null;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @CheckForNull
    public WaitingItem schedule(AbstractProject p) {
        return schedule(p, p.getQuietPeriod());
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Deprecated
    public boolean add(AbstractProject p, int quietPeriod) {
        return schedule(p, quietPeriod) != null;
    }

    @Deprecated
    public WaitingItem schedule(Task p, int quietPeriod, List<Action> actions) {
        return schedule2(p, quietPeriod, actions).getCreateItem();
    }

    @NonNull
    public ScheduleResult schedule2(Task p, int quietPeriod, List<Action> actions) {
        List<Action> actions2 = new ArrayList<>(actions);
        actions2.removeIf((v0) -> {
            return Objects.isNull(v0);
        });
        this.lock.lock();
        try {
            try {
                Iterator it = QueueDecisionHandler.all().iterator();
                while (it.hasNext()) {
                    QueueDecisionHandler h = (QueueDecisionHandler) it.next();
                    if (!h.shouldSchedule(p, actions2)) {
                        ScheduleResult.Refused refused = ScheduleResult.refused();
                        updateSnapshot();
                        this.lock.unlock();
                        return refused;
                    }
                }
                ScheduleResult scheduleInternal = scheduleInternal(p, quietPeriod, actions2);
                updateSnapshot();
                this.lock.unlock();
                return scheduleInternal;
            } catch (Throwable th) {
                updateSnapshot();
                throw th;
            }
        } catch (Throwable th2) {
            this.lock.unlock();
            throw th2;
        }
    }

    @NonNull
    private ScheduleResult scheduleInternal(Task p, int quietPeriod, List<Action> actions) {
        this.lock.lock();
        try {
            try {
                Calendar due = new GregorianCalendar();
                due.add(13, quietPeriod);
                List<Item> duplicatesInQueue = new ArrayList<>();
                for (Item item : liveGetItems(p)) {
                    boolean shouldScheduleItem = false;
                    for (QueueAction action : item.getActions(QueueAction.class)) {
                        shouldScheduleItem |= action.shouldSchedule(actions);
                    }
                    for (QueueAction action2 : Util.filter(actions, QueueAction.class)) {
                        shouldScheduleItem |= action2.shouldSchedule(new ArrayList(item.getAllActions()));
                    }
                    if (!shouldScheduleItem) {
                        duplicatesInQueue.add(item);
                    }
                }
                if (duplicatesInQueue.isEmpty()) {
                    LOGGER.log(Level.FINE, "{0} added to queue", p);
                    WaitingItem added = new WaitingItem(due, p, actions);
                    added.enter(this);
                    m44scheduleMaintenance();
                    ScheduleResult.Created created = ScheduleResult.created(added);
                    updateSnapshot();
                    this.lock.unlock();
                    return created;
                }
                LOGGER.log(Level.FINE, "{0} is already in the queue", p);
                for (Item item2 : duplicatesInQueue) {
                    for (FoldableAction a : Util.filter(actions, FoldableAction.class)) {
                        a.foldIntoExisting(item2, p, actions);
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.log(Level.FINE, "after folding {0}, {1} includes {2}", new Object[]{a, item2, item2.getAllActions()});
                        }
                    }
                }
                boolean queueUpdated = false;
                for (WaitingItem wi : Util.filter(duplicatesInQueue, WaitingItem.class)) {
                    if (!wi.timestamp.before(due)) {
                        wi.leave(this);
                        wi.timestamp = due;
                        wi.enter(this);
                        queueUpdated = true;
                    }
                }
                if (queueUpdated) {
                    m44scheduleMaintenance();
                }
                ScheduleResult.Existing existing = ScheduleResult.existing((Item) duplicatesInQueue.getFirst());
                updateSnapshot();
                this.lock.unlock();
                return existing;
            } catch (Throwable th) {
                updateSnapshot();
                throw th;
            }
        } catch (Throwable th2) {
            this.lock.unlock();
            throw th2;
        }
    }

    @Deprecated
    public boolean add(Task p, int quietPeriod) {
        return schedule(p, quietPeriod) != null;
    }

    @CheckForNull
    public WaitingItem schedule(Task p, int quietPeriod) {
        return schedule(p, quietPeriod, new Action[0]);
    }

    @Deprecated
    public boolean add(Task p, int quietPeriod, Action... actions) {
        return schedule(p, quietPeriod, actions) != null;
    }

    @CheckForNull
    public WaitingItem schedule(Task p, int quietPeriod, Action... actions) {
        return schedule2(p, quietPeriod, actions).getCreateItem();
    }

    @NonNull
    public ScheduleResult schedule2(Task p, int quietPeriod, Action... actions) {
        return schedule2(p, quietPeriod, Arrays.asList(actions));
    }

    public boolean cancel(Task p) {
        this.lock.lock();
        try {
            try {
                LOGGER.log(Level.FINE, "Cancelling {0}", p);
                for (WaitingItem item : this.waitingList) {
                    if (item.task.equals(p)) {
                        boolean cancel = item.cancel(this);
                        updateSnapshot();
                        this.lock.unlock();
                        return cancel;
                    }
                }
                boolean z = (this.blockedProjects.cancel(p) != null) | (this.buildables.cancel(p) != null);
                updateSnapshot();
                this.lock.unlock();
                return z;
            } catch (Throwable th) {
                updateSnapshot();
                throw th;
            }
        } catch (Throwable th2) {
            this.lock.unlock();
            throw th2;
        }
    }

    private void updateSnapshot() {
        Snapshot revised = new Snapshot(this.waitingList, this.blockedProjects, this.buildables, this.pendings);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0} → {1}; leftItems={2}", new Object[]{this.snapshot, revised, this.leftItems.asMap()});
        }
        this.snapshot = revised;
    }

    public boolean cancel(Item item) {
        LOGGER.log(Level.FINE, "Cancelling {0} item#{1}", new Object[]{item.task, Long.valueOf(item.id)});
        this.lock.lock();
        try {
            try {
                boolean cancel = item.cancel(this);
                this.lock.unlock();
                return cancel;
            } finally {
                updateSnapshot();
            }
        } catch (Throwable th) {
            this.lock.unlock();
            throw th;
        }
    }

    @RequirePOST
    public HttpResponse doCancelItem(@QueryParameter long id) throws IOException, ServletException {
        Item item = getItem(id);
        if (item != null && !hasReadPermission(item, true)) {
            item = null;
        }
        if (item != null) {
            if (item.hasCancelPermission()) {
                if (cancel(item)) {
                    return HttpResponses.status(204);
                }
                return HttpResponses.error(500, "Could not cancel run for id " + id);
            }
            return HttpResponses.error(422, "Item for id (" + id + ") is not cancellable");
        }
        return HttpResponses.error(404, "Provided id (" + id + ") not found");
    }

    public boolean isEmpty() {
        Snapshot snapshot = this.snapshot;
        return snapshot.waitingList.isEmpty() && snapshot.blockedProjects.isEmpty() && snapshot.buildables.isEmpty() && snapshot.pendings.isEmpty();
    }

    private WaitingItem peek() {
        return this.waitingList.iterator().next();
    }

    @Exported(inline = true)
    public Item[] getItems() {
        Snapshot s = this.snapshot;
        List<Item> r = new ArrayList();
        for (WaitingItem p : s.waitingList) {
            r = checkPermissionsAndAddToList(r, p);
        }
        for (BlockedItem p2 : s.blockedProjects) {
            r = checkPermissionsAndAddToList(r, p2);
        }
        for (BuildableItem p3 : Iterators.reverse(s.buildables)) {
            r = checkPermissionsAndAddToList(r, p3);
        }
        for (BuildableItem p4 : Iterators.reverse(s.pendings)) {
            r = checkPermissionsAndAddToList(r, p4);
        }
        Item[] items = new Item[r.size()];
        r.toArray(items);
        return items;
    }

    private List<Item> checkPermissionsAndAddToList(List<Item> r, Item t) {
        if (hasReadPermission(t.task, false)) {
            r.add(t);
        }
        return r;
    }

    private static boolean hasReadPermission(Item t, boolean valueIfNotAccessControlled) {
        return hasReadPermission(t.task, valueIfNotAccessControlled);
    }

    private static boolean hasReadPermission(Task t, boolean valueIfNotAccessControlled) {
        if (t instanceof AccessControlled) {
            AccessControlled taskAC = (AccessControlled) t;
            if (taskAC.hasPermission(hudson.model.Item.READ) || taskAC.hasPermission(Permission.READ)) {
                return true;
            }
            return false;
        }
        return valueIfNotAccessControlled;
    }

    @Exported(inline = true)
    @Restricted({NoExternalUse.class})
    public StubItem[] getDiscoverableItems() {
        Snapshot s = this.snapshot;
        List<StubItem> r = new ArrayList();
        for (WaitingItem p : s.waitingList) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p);
        }
        for (BlockedItem p2 : s.blockedProjects) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p2);
        }
        for (BuildableItem p3 : Iterators.reverse(s.buildables)) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p3);
        }
        for (BuildableItem p4 : Iterators.reverse(s.pendings)) {
            r = filterDiscoverableItemListBasedOnPermissions(r, p4);
        }
        StubItem[] items = new StubItem[r.size()];
        r.toArray(items);
        return items;
    }

    private List<StubItem> filterDiscoverableItemListBasedOnPermissions(List<StubItem> r, Item t) {
        hudson.model.Item item = t.task;
        if (item instanceof hudson.model.Item) {
            hudson.model.Item taskAsItem = item;
            if (!taskAsItem.hasPermission(hudson.model.Item.READ) && taskAsItem.hasPermission(hudson.model.Item.DISCOVER)) {
                r.add(new StubItem(new StubTask(t.task)));
            }
        }
        return r;
    }

    @Deprecated
    public List<Item> getApproximateItemsQuickly() {
        return Arrays.asList(getItems());
    }

    public Item getItem(long id) {
        Snapshot snapshot = this.snapshot;
        for (Item item : snapshot.blockedProjects) {
            if (item.id == id) {
                return item;
            }
        }
        for (Item item2 : snapshot.buildables) {
            if (item2.id == id) {
                return item2;
            }
        }
        for (Item item3 : snapshot.pendings) {
            if (item3.id == id) {
                return item3;
            }
        }
        for (Item item4 : snapshot.waitingList) {
            if (item4.id == id) {
                return item4;
            }
        }
        return (Item) this.leftItems.getIfPresent(Long.valueOf(id));
    }

    public List<BuildableItem> getBuildableItems(Computer c) {
        Snapshot snapshot = this.snapshot;
        List<BuildableItem> result = new ArrayList<>();
        _getBuildableItems(c, snapshot.buildables, result);
        _getBuildableItems(c, snapshot.pendings, result);
        return result;
    }

    private void _getBuildableItems(Computer c, List<BuildableItem> col, List<BuildableItem> result) {
        Node node = c.getNode();
        if (node == null) {
            return;
        }
        for (BuildableItem p : col) {
            if (node.canTake(p) == null) {
                result.add(p);
            }
        }
    }

    public List<BuildableItem> getBuildableItems() {
        Snapshot snapshot = this.snapshot;
        ArrayList<BuildableItem> r = new ArrayList<>(snapshot.buildables);
        r.addAll(snapshot.pendings);
        return r;
    }

    public List<BuildableItem> getPendingItems() {
        return new ArrayList(this.snapshot.pendings);
    }

    protected List<BlockedItem> getBlockedItems() {
        return new ArrayList(this.snapshot.blockedProjects);
    }

    public Collection<LeftItem> getLeftItems() {
        return Collections.unmodifiableCollection(this.leftItems.asMap().values());
    }

    public void clearLeftItems() {
        this.leftItems.invalidateAll();
    }

    public List<Item> getUnblockedItems() {
        Snapshot snapshot = this.snapshot;
        List<Item> queuedNotBlocked = new ArrayList<>();
        queuedNotBlocked.addAll(snapshot.waitingList);
        queuedNotBlocked.addAll(snapshot.buildables);
        queuedNotBlocked.addAll(snapshot.pendings);
        return queuedNotBlocked;
    }

    public Set<Task> getUnblockedTasks() {
        List<Item> items = getUnblockedItems();
        Set<Task> unblockedTasks = new HashSet<>(items.size());
        for (Item t : items) {
            unblockedTasks.add(t.task);
        }
        return unblockedTasks;
    }

    public boolean isPending(Task t) {
        Snapshot snapshot = this.snapshot;
        for (BuildableItem i : snapshot.pendings) {
            if (i.task.equals(t)) {
                return true;
            }
        }
        return false;
    }

    public int countBuildableItemsFor(@CheckForNull Label l) {
        Snapshot snapshot = this.snapshot;
        int r = 0;
        for (BuildableItem bi : snapshot.buildables) {
            for (SubTask st : bi.task.getSubTasks()) {
                if (null == l || bi.getAssignedLabelFor(st) == l) {
                    r++;
                }
            }
        }
        for (BuildableItem bi2 : snapshot.pendings) {
            for (SubTask st2 : bi2.task.getSubTasks()) {
                if (null == l || bi2.getAssignedLabelFor(st2) == l) {
                    r++;
                }
            }
        }
        return r;
    }

    public int strictCountBuildableItemsFor(@CheckForNull Label l) {
        Snapshot _snapshot = this.snapshot;
        int r = 0;
        for (BuildableItem bi : _snapshot.buildables) {
            for (SubTask st : bi.task.getSubTasks()) {
                if (bi.getAssignedLabelFor(st) == l) {
                    r++;
                }
            }
        }
        for (BuildableItem bi2 : _snapshot.pendings) {
            for (SubTask st2 : bi2.task.getSubTasks()) {
                if (bi2.getAssignedLabelFor(st2) == l) {
                    r++;
                }
            }
        }
        return r;
    }

    public int countBuildableItems() {
        return countBuildableItemsFor(null);
    }

    public Item getItem(Task t) {
        Snapshot snapshot = this.snapshot;
        for (Item item : snapshot.blockedProjects) {
            if (item.task.equals(t)) {
                return item;
            }
        }
        for (Item item2 : snapshot.buildables) {
            if (item2.task.equals(t)) {
                return item2;
            }
        }
        for (Item item3 : snapshot.pendings) {
            if (item3.task.equals(t)) {
                return item3;
            }
        }
        for (Item item4 : snapshot.waitingList) {
            if (item4.task.equals(t)) {
                return item4;
            }
        }
        return null;
    }

    private List<Item> liveGetItems(Task t) {
        this.lock.lock();
        try {
            List<Item> result = new ArrayList<>();
            result.addAll(this.blockedProjects.getAll(t));
            result.addAll(this.buildables.getAll(t));
            if (LOGGER.isLoggable(Level.FINE)) {
                List<BuildableItem> thePendings = this.pendings.getAll(t);
                if (!thePendings.isEmpty()) {
                    LOGGER.log(Level.FINE, "ignoring {0} during scheduleInternal", thePendings);
                }
            }
            for (Item item : this.waitingList) {
                if (item.task.equals(t)) {
                    result.add(item);
                }
            }
            return result;
        } finally {
            this.lock.unlock();
        }
    }

    public List<Item> getItems(Task t) {
        Snapshot snapshot = this.snapshot;
        List<Item> result = new ArrayList<>();
        for (Item item : snapshot.blockedProjects) {
            if (item.task.equals(t)) {
                result.add(item);
            }
        }
        for (Item item2 : snapshot.buildables) {
            if (item2.task.equals(t)) {
                result.add(item2);
            }
        }
        for (Item item3 : snapshot.pendings) {
            if (item3.task.equals(t)) {
                result.add(item3);
            }
        }
        for (Item item4 : snapshot.waitingList) {
            if (item4.task.equals(t)) {
                result.add(item4);
            }
        }
        return result;
    }

    public boolean contains(Task t) {
        return getItem(t) != null;
    }

    void onStartExecuting(Executor exec) throws InterruptedException {
        this.lock.lock();
        try {
            try {
                WorkUnit wu = exec.getCurrentWorkUnit();
                this.pendings.remove(wu.context.item);
                LeftItem li = new LeftItem(wu.context);
                li.enter(this);
                updateSnapshot();
            } catch (Throwable th) {
                updateSnapshot();
                throw th;
            }
        } finally {
            this.lock.unlock();
        }
    }

    @WithBridgeMethods({void.class})
    /* renamed from: scheduleMaintenance, reason: merged with bridge method [inline-methods] */
    public Future<?> m44scheduleMaintenance() {
        return this.maintainerThread.submit();
    }

    @CheckForNull
    private CauseOfBlockage getCauseOfBlockageForItem(Item i) {
        CauseOfBlockage causeOfBlockage;
        CauseOfBlockage causeOfBlockage2 = getCauseOfBlockageForTask(i.task);
        if (causeOfBlockage2 != null) {
            return causeOfBlockage2;
        }
        Iterator it = QueueTaskDispatcher.all().iterator();
        while (it.hasNext()) {
            QueueTaskDispatcher d = (QueueTaskDispatcher) it.next();
            try {
                causeOfBlockage = d.canRun(i);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t, () -> {
                    return String.format("Exception evaluating if the queue can run the task '%s'", i.task.getName());
                });
                causeOfBlockage = CauseOfBlockage.fromMessage(Messages._Queue_ExceptionCanRun());
            }
            if (causeOfBlockage != null) {
                return causeOfBlockage;
            }
        }
        if ((i instanceof BuildableItem) || i.task.isConcurrentBuild()) {
            return null;
        }
        if (this.buildables.containsKey(i.task) || this.pendings.containsKey(i.task)) {
            return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
        }
        return null;
    }

    @CheckForNull
    private CauseOfBlockage getCauseOfBlockageForTask(Task task) {
        ResourceActivity r;
        CauseOfBlockage causeOfBlockage = task.getCauseOfBlockage();
        if (causeOfBlockage != null) {
            return causeOfBlockage;
        }
        if (!canRun(task.getResourceList()) && (r = getBlockingActivity(task)) != null) {
            if (r == task) {
                return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
            }
            return CauseOfBlockage.fromMessage(Messages._Queue_BlockedBy(r.getDisplayName()));
        }
        return null;
    }

    public static <T extends Throwable> void runWithLock(ThrowingRunnable<T> runnable) throws Throwable {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        if (queue == null) {
            runnable.run();
        } else {
            queue._runWithLock(runnable);
        }
    }

    public static void withLock(Runnable runnable) {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        if (queue == null) {
            runnable.run();
        } else {
            queue._withLock(runnable);
        }
    }

    public static <V, T extends Throwable> V callWithLock(ThrowingCallable<V, T> throwingCallable) throws Throwable {
        Jenkins instanceOrNull = Jenkins.getInstanceOrNull();
        Queue queue = instanceOrNull == null ? null : instanceOrNull.getQueue();
        if (queue == null) {
            return (V) throwingCallable.call();
        }
        return (V) queue._callWithLock(throwingCallable);
    }

    public static <V, T extends Throwable> V withLock(hudson.remoting.Callable<V, T> callable) throws Throwable {
        Jenkins instanceOrNull = Jenkins.getInstanceOrNull();
        Queue queue = instanceOrNull == null ? null : instanceOrNull.getQueue();
        if (queue == null) {
            return (V) callable.call();
        }
        return (V) queue._withLock(callable);
    }

    public static <V> V withLock(Callable<V> callable) throws Exception {
        Jenkins instanceOrNull = Jenkins.getInstanceOrNull();
        Queue queue = instanceOrNull == null ? null : instanceOrNull.getQueue();
        if (queue == null) {
            return callable.call();
        }
        return (V) queue._withLock(callable);
    }

    public static boolean tryWithLock(Runnable runnable) {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        if (queue == null) {
            runnable.run();
            return true;
        }
        return queue._tryWithLock(runnable);
    }

    public static boolean tryWithLock(Runnable runnable, Duration timeout) throws InterruptedException {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        if (queue == null) {
            runnable.run();
            return true;
        }
        return queue._tryWithLock(runnable, timeout);
    }

    public static Runnable wrapWithLock(Runnable runnable) {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        return queue == null ? runnable : new LockedRunnable(runnable);
    }

    public static <V, T extends Throwable> hudson.remoting.Callable<V, T> wrapWithLock(hudson.remoting.Callable<V, T> callable) {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        return queue == null ? callable : new LockedHRCallable(callable);
    }

    public static <V> Callable<V> wrapWithLock(Callable<V> callable) {
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        Queue queue = jenkins2 == null ? null : jenkins2.getQueue();
        return queue == null ? callable : new LockedJUCCallable(callable);
    }

    @SuppressFBWarnings(value = {"WA_AWAIT_NOT_IN_LOOP"}, justification = "the caller does indeed call this method in a loop")
    protected void _await() throws InterruptedException {
        this.condition.await();
    }

    protected void _signalAll() {
        this.condition.signalAll();
    }

    protected <T extends Throwable> void _runWithLock(ThrowingRunnable<T> runnable) throws Throwable {
        this.lock.lock();
        try {
            runnable.run();
        } finally {
            this.lock.unlock();
        }
    }

    protected void _withLock(Runnable runnable) {
        this.lock.lock();
        try {
            runnable.run();
        } finally {
            this.lock.unlock();
        }
    }

    protected boolean _tryWithLock(Runnable runnable) {
        if (this.lock.tryLock()) {
            try {
                runnable.run();
                return true;
            } finally {
                this.lock.unlock();
            }
        }
        return false;
    }

    protected boolean _tryWithLock(Runnable runnable, Duration timeout) throws InterruptedException {
        if (this.lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
            try {
                runnable.run();
                return true;
            } finally {
                this.lock.unlock();
            }
        }
        return false;
    }

    protected <V, T extends Throwable> V _callWithLock(ThrowingCallable<V, T> throwingCallable) throws Throwable {
        this.lock.lock();
        try {
            return (V) throwingCallable.call();
        } finally {
            this.lock.unlock();
        }
    }

    protected <V, T extends Throwable> V _withLock(hudson.remoting.Callable<V, T> callable) throws Throwable {
        this.lock.lock();
        try {
            return (V) callable.call();
        } finally {
            this.lock.unlock();
        }
    }

    protected <V> V _withLock(Callable<V> callable) throws Exception {
        this.lock.lock();
        try {
            return callable.call();
        } finally {
            this.lock.unlock();
        }
    }

    /* JADX WARN: Finally extract failed */
    public void maintain() {
        CauseOfBlockage reason;
        Jenkins jenkins2 = Jenkins.getInstanceOrNull();
        if (jenkins2 == null) {
            return;
        }
        this.lock.lock();
        try {
            try {
                LOGGER.log(Level.FINE, "Queue maintenance started on {0} with {1}", new Object[]{this, this.snapshot});
                Map<Executor, JobOffer> parked = new HashMap<>();
                List<BuildableItem> lostPendings = new ArrayList<>(this.pendings);
                for (Computer c : jenkins2.getComputers()) {
                    for (Executor e : c.getAllExecutors()) {
                        if (e.isInterrupted()) {
                            lostPendings.clear();
                            LOGGER.log(Level.FINEST, "Interrupt thread for executor {0} is set and we do not know what work unit was on the executor.", e.getDisplayName());
                        } else {
                            if (e.isParking()) {
                                LOGGER.log(Level.FINEST, "{0} is parking and is waiting for a job to execute.", e.getDisplayName());
                                parked.put(e, new JobOffer(e));
                            }
                            WorkUnit workUnit = e.getCurrentWorkUnit();
                            if (workUnit != null) {
                                lostPendings.remove(workUnit.context.item);
                            }
                        }
                    }
                }
                for (BuildableItem p : lostPendings) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "BuildableItem {0}: pending -> buildable as the assigned executor disappeared", p.task.getFullDisplayName());
                    }
                    p.isPending = false;
                    this.pendings.remove(p);
                    Runnable r = makeBuildable(p);
                    if (r != null) {
                        LOGGER.fine(() -> {
                            return "Executing lost runnable " + p.task.getFullDisplayName();
                        });
                        r.run();
                    }
                }
                QueueSorter s = this.sorter;
                List<BlockedItem> blockedItems = new ArrayList<>(this.blockedProjects);
                if (s != null) {
                    s.sortBlockedItems(blockedItems);
                } else {
                    blockedItems.sort(QueueSorter.DEFAULT_BLOCKED_ITEM_COMPARATOR);
                }
                for (BlockedItem p2 : blockedItems) {
                    String taskDisplayName = LOGGER.isLoggable(Level.FINEST) ? p2.task.getFullDisplayName() : null;
                    LOGGER.log(Level.FINEST, "Current blocked item: {0}", taskDisplayName);
                    CauseOfBlockage causeOfBlockage = getCauseOfBlockageForItem(p2);
                    if (causeOfBlockage == null) {
                        LOGGER.log(Level.FINEST, "BlockedItem {0}: blocked -> buildable as the build is not blocked and new tasks are allowed", taskDisplayName);
                        Runnable r2 = makeBuildable(new BuildableItem(p2));
                        if (r2 != null) {
                            p2.leave(this);
                            r2.run();
                            updateSnapshot();
                        }
                    } else if (causeOfBlockage.isFatal()) {
                        cancel(p2);
                    } else {
                        p2.leave(this);
                        new BlockedItem(p2, causeOfBlockage).enter(this);
                        updateSnapshot();
                    }
                }
                while (true) {
                    if (this.waitingList.isEmpty()) {
                        break;
                    }
                    WaitingItem top = peek();
                    if (top.timestamp.compareTo((Calendar) new GregorianCalendar()) > 0) {
                        LOGGER.log(Level.FINEST, "Finished moving all ready items from queue.");
                        break;
                    }
                    CauseOfBlockage causeOfBlockage2 = getCauseOfBlockageForItem(top);
                    if (causeOfBlockage2 == null) {
                        top.leave(this);
                        Runnable r3 = makeBuildable(new BuildableItem(top));
                        String topTaskDisplayName = LOGGER.isLoggable(Level.FINEST) ? top.task.getFullDisplayName() : null;
                        if (r3 != null) {
                            LOGGER.log(Level.FINEST, "Executing runnable {0}", topTaskDisplayName);
                            r3.run();
                        } else {
                            LOGGER.log(Level.FINEST, "Item {0} was unable to be made a buildable and is now a blocked item.", topTaskDisplayName);
                            new BlockedItem(top, CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown())).enter(this);
                        }
                    } else if (causeOfBlockage2.isFatal()) {
                        cancel(top);
                    } else {
                        top.leave(this);
                        new BlockedItem(top, causeOfBlockage2).enter(this);
                    }
                }
                if (s != null) {
                    try {
                        s.sortBuildableItems(this.buildables);
                    } catch (Throwable e2) {
                        LOGGER.log(Level.WARNING, "s.sortBuildableItems() threw Throwable: {0}", e2);
                    }
                }
                updateSnapshot();
                Iterator it = new ArrayList(this.buildables).iterator();
                while (it.hasNext()) {
                    BuildableItem p3 = (BuildableItem) it.next();
                    CauseOfBlockage causeOfBlockage3 = getCauseOfBlockageForItem(p3);
                    if (causeOfBlockage3 != null) {
                        if (causeOfBlockage3.isFatal()) {
                            cancel(p3);
                        } else {
                            p3.leave(this);
                            new BlockedItem(p3, causeOfBlockage3).enter(this);
                            LOGGER.log(Level.FINE, "Catching that {0} is blocked in the last minute", p3);
                            updateSnapshot();
                        }
                    } else {
                        String taskDisplayName2 = LOGGER.isLoggable(Level.FINEST) ? p3.task.getFullDisplayName() : null;
                        if (p3.task instanceof FlyweightTask) {
                            Runnable r4 = makeFlyWeightTaskBuildable(new BuildableItem(p3));
                            if (r4 != null) {
                                p3.leave(this);
                                LOGGER.log(Level.FINEST, "Executing flyweight task {0}", taskDisplayName2);
                                r4.run();
                                updateSnapshot();
                            }
                        } else {
                            List<JobOffer> candidates = new ArrayList<>(parked.size());
                            Map<Node, CauseOfBlockage> reasonMap = new HashMap<>();
                            for (JobOffer j : parked.values()) {
                                Node offerNode = j.getNode();
                                if (reasonMap.containsKey(offerNode)) {
                                    reason = reasonMap.get(offerNode);
                                } else {
                                    reason = j.getCauseOfBlockage(p3);
                                    reasonMap.put(offerNode, reason);
                                }
                                if (reason == null) {
                                    LOGGER.log(Level.FINEST, "{0} is a potential candidate for task {1}", new Object[]{j, taskDisplayName2});
                                    candidates.add(j);
                                } else {
                                    LOGGER.log(Level.FINEST, "{0} rejected {1}: {2}", new Object[]{j, taskDisplayName2, reason});
                                }
                            }
                            MappingWorksheet ws = new MappingWorksheet(p3, candidates);
                            MappingWorksheet.Mapping m = this.loadBalancer.map(p3.task, ws);
                            if (m == null) {
                                LOGGER.log(Level.FINER, "Failed to map {0} to executors. candidates={1} parked={2}", new Object[]{p3, candidates, parked.values()});
                                List<CauseOfBlockage> reasons = (List) reasonMap.values().stream().filter((v0) -> {
                                    return Objects.nonNull(v0);
                                }).collect(Collectors.toList());
                                p3.transientCausesOfBlockage = reasons.isEmpty() ? null : reasons;
                            } else {
                                WorkUnitContext wuc = new WorkUnitContext(p3);
                                LOGGER.log(Level.FINEST, "Found a matching executor for {0}. Using it.", taskDisplayName2);
                                m.execute(wuc);
                                p3.leave(this);
                                if (!wuc.getWorkUnits().isEmpty()) {
                                    LOGGER.log(Level.FINEST, "BuildableItem {0} marked as pending.", taskDisplayName2);
                                    makePending(p3);
                                } else {
                                    LOGGER.log(Level.FINEST, "BuildableItem {0} with empty work units!?", p3);
                                }
                                updateSnapshot();
                            }
                        }
                    }
                }
                updateSnapshot();
            } catch (Throwable th) {
                updateSnapshot();
                throw th;
            }
        } finally {
            this.lock.unlock();
        }
    }

    @CheckForNull
    private Runnable makeBuildable(final BuildableItem p) {
        if (p.task instanceof FlyweightTask) {
            String taskDisplayName = LOGGER.isLoggable(Level.FINEST) ? p.task.getFullDisplayName() : null;
            if (!isBlockedByShutdown(p.task)) {
                Runnable runnable = makeFlyWeightTaskBuildable(p);
                LOGGER.log(Level.FINEST, "Converting flyweight task: {0} into a BuildableRunnable", taskDisplayName);
                if (runnable != null) {
                    return runnable;
                }
                LOGGER.log(Level.FINEST, "Flyweight task {0} is entering as buildable to provision a node.", taskDisplayName);
                return new BuildableRunnable(p);
            }
            LOGGER.log(Level.FINEST, "Task {0} is blocked by shutdown.", taskDisplayName);
            return null;
        }
        return new BuildableRunnable(p);
    }

    @CheckForNull
    private Runnable makeFlyWeightTaskBuildable(final BuildableItem p) {
        if (p.task instanceof FlyweightTask) {
            Jenkins h = Jenkins.get();
            Label lbl = p.getAssignedLabel();
            Computer masterComputer = h.toComputer();
            if (lbl != null && lbl.equals(h.getSelfLabel()) && masterComputer != null) {
                if (h.canTake(p) == null) {
                    return createFlyWeightTaskRunnable(p, masterComputer);
                }
                return null;
            }
            if (lbl == null && h.canTake(p) == null && masterComputer != null && masterComputer.isOnline() && masterComputer.isAcceptingTasks()) {
                return createFlyWeightTaskRunnable(p, masterComputer);
            }
            Map<Node, Integer> hashSource = new HashMap<>(h.getNodes().size());
            for (Node n : h.getNodes()) {
                hashSource.put(n, Integer.valueOf(n.getNumExecutors() * 100));
            }
            ConsistentHash<Node> hash = new ConsistentHash<>(NODE_HASH);
            hash.addAll(hashSource);
            String fullDisplayName = p.task.getFullDisplayName();
            for (Node n2 : hash.list(fullDisplayName)) {
                Computer c = n2.toComputer();
                if (c != null && !c.isOffline() && (lbl == null || lbl.contains(n2))) {
                    if (n2.canTake(p) == null) {
                        return createFlyWeightTaskRunnable(p, c);
                    }
                }
            }
            return null;
        }
        return null;
    }

    private Runnable createFlyWeightTaskRunnable(final BuildableItem p, @NonNull final Computer c) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Creating flyweight task {0} for computer {1}", new Object[]{p.task.getFullDisplayName(), c.getName()});
        }
        return () -> {
            c.startFlyWeightTask(new WorkUnitContext(p).createWorkUnit(p.task));
            makePending(p);
        };
    }

    static {
        XSTREAM.registerConverter(new AbstractSingleValueConverter() { // from class: hudson.model.Queue.2
            public boolean canConvert(Class klazz) {
                return hudson.model.Item.class.isAssignableFrom(klazz);
            }

            public Object fromString(String string) {
                Object item = Jenkins.get().getItemByFullName(string);
                if (item == null) {
                    throw new NoSuchElementException("No such job exists: " + string);
                }
                return item;
            }

            public String toString(Object item) {
                return ((hudson.model.Item) item).getFullName();
            }
        });
        XSTREAM.registerConverter(new AbstractSingleValueConverter() { // from class: hudson.model.Queue.3
            public boolean canConvert(Class klazz) {
                return Run.class.isAssignableFrom(klazz);
            }

            public Object fromString(String string) {
                String[] split = string.split("#");
                String projectName = split[0];
                int buildNumber = Integer.parseInt(split[1]);
                Job<?, ?> job = (Job) Jenkins.get().getItemByFullName(projectName);
                if (job == null) {
                    throw new NoSuchElementException("No such job exists: " + projectName);
                }
                Run mo10getBuildByNumber = job.mo10getBuildByNumber(buildNumber);
                if (mo10getBuildByNumber == null) {
                    throw new NoSuchElementException("No such build: " + string);
                }
                return mo10getBuildByNumber;
            }

            public String toString(Object object) {
                Run<?, ?> run = (Run) object;
                return run.getParent().getFullName() + "#" + run.getNumber();
            }
        });
        XSTREAM.registerConverter(new AbstractSingleValueConverter() { // from class: hudson.model.Queue.4
            public boolean canConvert(Class klazz) {
                return Queue.class.isAssignableFrom(klazz);
            }

            public Object fromString(String string) {
                return Jenkins.get().getQueue();
            }

            public String toString(Object item) {
                return "queue";
            }
        });
    }

    private boolean makePending(BuildableItem p) {
        p.isPending = true;
        return this.pendings.add(p);
    }

    @Deprecated
    public static boolean ifBlockedByHudsonShutdown(Task task) {
        return isBlockedByShutdown(task);
    }

    public static boolean isBlockedByShutdown(Task task) {
        return Jenkins.get().isQuietingDown() && !(task instanceof NonBlockingTask);
    }

    public Api getApi() {
        return new Api(this);
    }

    /* loaded from: Queue$Task.class */
    public interface Task extends FullyNamedModelObject, SubTask {
        String getName();

        String getUrl();

        @Deprecated
        default boolean isBuildBlocked() {
            return getCauseOfBlockage() != null;
        }

        @Deprecated
        default String getWhyBlocked() {
            CauseOfBlockage cause = getCauseOfBlockage();
            if (cause != null) {
                return cause.getShortDescription();
            }
            return null;
        }

        @CheckForNull
        default CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        default String getAffinityKey() {
            if (this instanceof FullyNamed) {
                FullyNamed fullyNamed = (FullyNamed) this;
                return fullyNamed.getFullName();
            }
            return getFullDisplayName();
        }

        default void checkAbortPermission() {
            if (this instanceof AccessControlled) {
                ((AccessControlled) this).checkPermission(hudson.model.Item.CANCEL);
            }
        }

        default boolean isConcurrentBuild() {
            return false;
        }

        default Collection<? extends SubTask> getSubTasks() {
            return Set.of(this);
        }

        @NonNull
        default Authentication getDefaultAuthentication2() {
            if (Util.isOverridden(Task.class, getClass(), "getDefaultAuthentication", new Class[0])) {
                return getDefaultAuthentication().toSpring();
            }
            return ACL.SYSTEM2;
        }

        @NonNull
        @Deprecated
        default org.acegisecurity.Authentication getDefaultAuthentication() {
            return org.acegisecurity.Authentication.fromSpring(getDefaultAuthentication2());
        }

        @NonNull
        default Authentication getDefaultAuthentication2(Item item) {
            if (Util.isOverridden(Task.class, getClass(), "getDefaultAuthentication", new Class[]{Item.class})) {
                return getDefaultAuthentication(item).toSpring();
            }
            return getDefaultAuthentication2();
        }

        @NonNull
        @Deprecated
        default org.acegisecurity.Authentication getDefaultAuthentication(Item item) {
            return org.acegisecurity.Authentication.fromSpring(getDefaultAuthentication2(item));
        }
    }

    @StaplerAccessibleType
    /* loaded from: Queue$Executable.class */
    public interface Executable extends Runnable, WithConsoleUrl {
        @NonNull
        SubTask getParent();

        @Override // java.lang.Runnable
        void run() throws AsynchronousExecution;

        String toString();

        @CheckForNull
        default Executable getParentExecutable() {
            return null;
        }

        default long getEstimatedDuration() {
            return Executables.getParentOf(this).getEstimatedDuration();
        }

        default String getConsoleUrl() {
            Executable parent = getParentExecutable();
            if (parent != null) {
                return parent.getConsoleUrl();
            }
            return null;
        }
    }

    @ExportedBean(defaultVisibility = 999)
    @BridgeMethodsAdded
    /* loaded from: Queue$Item.class */
    public static abstract class Item extends Actionable implements QueueItem {
        private final long id;

        @Exported
        @NonNull
        public final Task task;
        private transient FutureImpl future;
        private final long inQueueSince;

        public abstract CauseOfBlockage getCauseOfBlockage();

        abstract void enter(Queue q);

        abstract boolean leave(Queue q);

        @Exported
        public long getId() {
            return this.id;
        }

        @Deprecated
        public int getIdLegacy() {
            if (this.id > 2147483647L) {
                throw new IllegalStateException("Sorry, you need to update any Plugins attempting to assign 'Queue.Item.id' to an int value. 'Queue.Item.id' is now a long value and has incremented to a value greater than Integer.MAX_VALUE (2^31 - 1).");
            }
            return (int) this.id;
        }

        @NonNull
        public Task getTask() {
            return this.task;
        }

        @Exported
        public boolean isBlocked() {
            return this instanceof BlockedItem;
        }

        @Exported
        public boolean isBuildable() {
            return this instanceof BuildableItem;
        }

        @Exported
        public boolean isStuck() {
            return false;
        }

        @Exported
        public long getInQueueSince() {
            return this.inQueueSince;
        }

        public String getInQueueForString() {
            long duration = System.currentTimeMillis() - this.inQueueSince;
            return Util.getTimeSpanString(duration);
        }

        @WithBridgeMethods({Future.class})
        /* renamed from: getFuture, reason: merged with bridge method [inline-methods] */
        public QueueTaskFuture<Executable> m45getFuture() {
            return this.future;
        }

        @CheckForNull
        public Label getAssignedLabel() {
            for (LabelAssignmentAction laa : getActions(LabelAssignmentAction.class)) {
                Label l = laa.getAssignedLabel(this.task);
                if (l != null) {
                    return l;
                }
            }
            return this.task.getAssignedLabel();
        }

        @CheckForNull
        public Label getAssignedLabelFor(@NonNull SubTask st) {
            for (LabelAssignmentAction laa : getActions(LabelAssignmentAction.class)) {
                Label l = laa.getAssignedLabel(st);
                if (l != null) {
                    return l;
                }
            }
            return st.getAssignedLabel();
        }

        public final List<Cause> getCauses() {
            CauseAction ca = getAction(CauseAction.class);
            if (ca != null) {
                return Collections.unmodifiableList(ca.getCauses());
            }
            return Collections.emptyList();
        }

        private Map<Cause, Integer> getCauseCounts() {
            CauseAction ca = getAction(CauseAction.class);
            if (ca != null) {
                return ca.getCauseCounts();
            }
            return Collections.emptyMap();
        }

        @Restricted({DoNotUse.class})
        public String getCausesDescription() {
            Map<Cause, Integer> causeCounts = getCauseCounts();
            StringBuilder s = new StringBuilder();
            causeCounts.forEach((ca, count) -> {
                s.append(ca.getShortDescription());
                if (count.intValue() > 1) {
                    s.append(" ").append(Messages._Queue_Ntimes(count));
                }
                s.append('\n');
            });
            return s.toString();
        }

        protected Item(@NonNull Task task, @NonNull List<Action> actions, long id, FutureImpl future) {
            this(task, actions, id, future, System.currentTimeMillis());
        }

        protected Item(@NonNull Task task, @NonNull List<Action> actions, long id, FutureImpl future, long inQueueSince) {
            this.task = task;
            this.id = id;
            this.future = future;
            this.inQueueSince = inQueueSince;
            for (Action action : actions) {
                addAction(action);
            }
        }

        protected Item(Item item) {
            this(item.task, new ArrayList(item.getActions()), item.id, item.future, item.inQueueSince);
        }

        @Exported
        public String getUrl() {
            return "queue/item/" + this.id + "/";
        }

        @Exported
        public final String getWhy() {
            CauseOfBlockage cob = getCauseOfBlockage();
            if (cob != null) {
                return cob.getShortDescription();
            }
            return null;
        }

        @Exported
        public String getParams() {
            StringBuilder s = new StringBuilder();
            for (ParametersAction pa : getActions(ParametersAction.class)) {
                for (ParameterValue p : pa.getParameters()) {
                    s.append('\n').append(p.getShortDescription());
                }
            }
            return s.toString();
        }

        public String getSearchUrl() {
            return null;
        }

        @RequirePOST
        @Deprecated
        public HttpResponse doCancelQueue() {
            if (!Queue.hasReadPermission(this, true)) {
                throw new CancelRequestHandlingException();
            }
            if (hasCancelPermission()) {
                Jenkins.get().getQueue().cancel(this);
            }
            return HttpResponses.status(204);
        }

        @NonNull
        public Authentication authenticate2() {
            for (QueueItemAuthenticator auth : QueueItemAuthenticatorProvider.authenticators()) {
                Authentication a = auth.authenticate2(this);
                if (a != null) {
                    return a;
                }
            }
            return this.task.getDefaultAuthentication2(this);
        }

        @Deprecated
        public org.acegisecurity.Authentication authenticate() {
            return org.acegisecurity.Authentication.fromSpring(authenticate2());
        }

        @Restricted({DoNotUse.class})
        public Api getApi() throws AccessDeniedException {
            AccessControlled accessControlled = this.task;
            if (accessControlled instanceof AccessControlled) {
                AccessControlled ac = accessControlled;
                if (!ac.hasPermission(hudson.model.Item.DISCOVER)) {
                    return null;
                }
                if (!ac.hasPermission(hudson.model.Item.READ)) {
                    throw new AccessDeniedException("Please log in to access " + this.task.getUrl());
                }
                return new Api(this);
            }
            return null;
        }

        public HttpResponse doIndex(StaplerRequest2 req) {
            return HttpResponses.text("Queue item exists. For details check, for example, " + req.getRequestURI() + "api/json?tree=cancelled,executable[url]");
        }

        protected Object readResolve() {
            this.future = new FutureImpl(this.task);
            return this;
        }

        public String toString() {
            return getClass().getName() + ":" + String.valueOf(this.task) + ":" + this.id;
        }

        boolean cancel(Queue q) {
            boolean r = leave(q);
            if (r) {
                this.future.setAsCancelled();
                LeftItem li = new LeftItem(this);
                li.enter(q);
            }
            return r;
        }
    }

    @Restricted({NoExternalUse.class})
    @ExportedBean(defaultVisibility = 999)
    /* loaded from: Queue$StubTask.class */
    public static class StubTask {
        private final String name;

        public StubTask(@NonNull Task base) {
            this.name = base.getName();
        }

        @Exported
        public String getName() {
            return this.name;
        }
    }

    @Restricted({NoExternalUse.class})
    @SuppressFBWarnings(value = {"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"}, justification = "read by Stapler")
    @ExportedBean(defaultVisibility = 999)
    /* loaded from: Queue$StubItem.class */
    public static class StubItem {

        @Exported
        public StubTask task;

        public StubItem(StubTask task) {
            this.task = task;
        }
    }

    /* loaded from: Queue$QueueDecisionHandler.class */
    public static abstract class QueueDecisionHandler implements ExtensionPoint {
        public abstract boolean shouldSchedule(Task p, List<Action> actions);

        public static ExtensionList<QueueDecisionHandler> all() {
            return ExtensionList.lookup(QueueDecisionHandler.class);
        }
    }

    /* loaded from: Queue$WaitingItem.class */
    public static final class WaitingItem extends Item implements Comparable<WaitingItem> {

        @Exported
        public Calendar timestamp;

        public WaitingItem(Calendar timestamp, Task project, List<Action> actions) {
            super(project, actions, QueueIdStrategy.get().generateIdFor(project, actions), new FutureImpl(project));
            this.timestamp = timestamp;
        }

        @Override // java.lang.Comparable
        public int compareTo(WaitingItem that) {
            int r = this.timestamp.getTime().compareTo(that.timestamp.getTime());
            return r != 0 ? r : Long.compare(getId(), that.getId());
        }

        @Override // hudson.model.Queue.Item
        public CauseOfBlockage getCauseOfBlockage() {
            long diff = this.timestamp.getTimeInMillis() - System.currentTimeMillis();
            if (diff >= 0) {
                return CauseOfBlockage.fromMessage(Messages._Queue_InQuietPeriod(Util.getTimeSpanString(diff)));
            }
            return CauseOfBlockage.fromMessage(Messages._Queue_FinishedWaiting());
        }

        @Override // hudson.model.Queue.Item
        void enter(Queue q) {
            if (q.waitingList.add(this)) {
                Listeners.notify(QueueListener.class, true, l -> {
                    l.onEnterWaiting(this);
                });
            }
        }

        @Override // hudson.model.Queue.Item
        boolean leave(Queue q) {
            boolean r = q.waitingList.remove(this);
            if (r) {
                Listeners.notify(QueueListener.class, true, l -> {
                    l.onLeaveWaiting(this);
                });
            }
            return r;
        }
    }

    /* loaded from: Queue$NotWaitingItem.class */
    public static abstract class NotWaitingItem extends Item {

        @Exported
        public final long buildableStartMilliseconds;

        protected NotWaitingItem(WaitingItem wi) {
            super(wi);
            this.buildableStartMilliseconds = System.currentTimeMillis();
        }

        protected NotWaitingItem(NotWaitingItem ni) {
            super(ni);
            this.buildableStartMilliseconds = ni.buildableStartMilliseconds;
        }
    }

    /* loaded from: Queue$BlockedItem.class */
    public final class BlockedItem extends NotWaitingItem {
        private final transient CauseOfBlockage causeOfBlockage;

        BlockedItem(WaitingItem wi, CauseOfBlockage causeOfBlockage) {
            super(wi);
            this.causeOfBlockage = causeOfBlockage;
        }

        BlockedItem(NotWaitingItem ni, CauseOfBlockage causeOfBlockage) {
            super(ni);
            this.causeOfBlockage = causeOfBlockage;
        }

        @Restricted({NoExternalUse.class})
        public boolean isCauseOfBlockageNull() {
            if (this.causeOfBlockage == null) {
                return true;
            }
            return false;
        }

        @Override // hudson.model.Queue.Item
        public CauseOfBlockage getCauseOfBlockage() {
            if (this.causeOfBlockage != null) {
                return this.causeOfBlockage;
            }
            return Queue.this.getCauseOfBlockageForItem(this);
        }

        @Override // hudson.model.Queue.Item
        void enter(Queue q) {
            Queue.LOGGER.log(Level.FINE, "{0} is blocked", this);
            Queue.this.blockedProjects.add(this);
            Listeners.notify(QueueListener.class, true, l -> {
                l.onEnterBlocked(this);
            });
        }

        @Override // hudson.model.Queue.Item
        boolean leave(Queue q) {
            boolean r = Queue.this.blockedProjects.remove(this);
            if (r) {
                Queue.LOGGER.log(Level.FINE, "{0} no longer blocked", this);
                Listeners.notify(QueueListener.class, true, l -> {
                    l.onLeaveBlocked(this);
                });
            }
            return r;
        }
    }

    /* loaded from: Queue$BuildableItem.class */
    public static final class BuildableItem extends NotWaitingItem {
        private boolean isPending;

        @CheckForNull
        private volatile transient List<CauseOfBlockage> transientCausesOfBlockage;

        public BuildableItem(WaitingItem wi) {
            super(wi);
        }

        public BuildableItem(NotWaitingItem ni) {
            super(ni);
        }

        @Override // hudson.model.Queue.Item
        public CauseOfBlockage getCauseOfBlockage() {
            Jenkins jenkins2 = Jenkins.get();
            if (Queue.isBlockedByShutdown(this.task)) {
                return CauseOfBlockage.fromMessage(Messages._Queue_HudsonIsAboutToShutDown());
            }
            List<CauseOfBlockage> causesOfBlockage = this.transientCausesOfBlockage;
            Label label = getAssignedLabel();
            List<Node> allNodes = jenkins2.getNodes();
            if (allNodes.isEmpty()) {
                label = null;
            }
            if (label != null) {
                Set<Node> nodes = label.getNodes();
                if (label.isOffline()) {
                    return nodes.size() != 1 ? new CauseOfBlockage.BecauseLabelIsOffline(label) : new CauseOfBlockage.BecauseNodeIsOffline(nodes.iterator().next());
                }
                if (causesOfBlockage == null || label.getIdleExecutors() <= 0) {
                    return nodes.size() != 1 ? new CauseOfBlockage.BecauseLabelIsBusy(label) : new CauseOfBlockage.BecauseNodeIsBusy(nodes.iterator().next());
                }
                return new CompositeCauseOfBlockage(causesOfBlockage);
            }
            if (causesOfBlockage != null && new ComputerSet().getIdleExecutors() > 0) {
                return new CompositeCauseOfBlockage(causesOfBlockage);
            }
            return CauseOfBlockage.createNeedsMoreExecutor(Messages._Queue_WaitingForNextAvailableExecutor());
        }

        @Override // hudson.model.Queue.Item
        public boolean isStuck() {
            Label label = getAssignedLabel();
            if (label != null && label.isOffline()) {
                return true;
            }
            long d = this.task.getEstimatedDuration();
            long elapsed = System.currentTimeMillis() - this.buildableStartMilliseconds;
            return d >= 0 ? elapsed > Math.max(d, 60000L) * 10 : TimeUnit.MILLISECONDS.toHours(elapsed) > 24;
        }

        @Exported
        public boolean isPending() {
            return this.isPending;
        }

        @Override // hudson.model.Queue.Item
        void enter(Queue q) {
            q.buildables.add(this);
            Listeners.notify(QueueListener.class, true, l -> {
                l.onEnterBuildable(this);
            });
        }

        @Override // hudson.model.Queue.Item
        boolean leave(Queue q) {
            boolean r = q.buildables.remove(this);
            if (r) {
                Queue.LOGGER.log(Level.FINE, "{0} no longer blocked", this);
                Listeners.notify(QueueListener.class, true, l -> {
                    l.onLeaveBuildable(this);
                });
            }
            return r;
        }
    }

    /* loaded from: Queue$LeftItem.class */
    public static final class LeftItem extends Item {
        public final WorkUnitContext outcome;

        public LeftItem(WorkUnitContext wuc) {
            super(wuc.item);
            this.outcome = wuc;
        }

        public LeftItem(Item cancelled) {
            super(cancelled);
            this.outcome = null;
        }

        @Override // hudson.model.Queue.Item
        public CauseOfBlockage getCauseOfBlockage() {
            return null;
        }

        @Exported
        @CheckForNull
        public Executable getExecutable() {
            if (this.outcome != null) {
                return this.outcome.getPrimaryWorkUnit().getExecutable();
            }
            return null;
        }

        @Exported
        public boolean isCancelled() {
            return this.outcome == null;
        }

        @Override // hudson.model.Queue.Item
        void enter(Queue q) {
            q.leftItems.put(Long.valueOf(getId()), this);
            Listeners.notify(QueueListener.class, true, l -> {
                l.onLeft(this);
            });
        }

        @Override // hudson.model.Queue.Item
        boolean leave(Queue q) {
            return false;
        }
    }

    /* loaded from: Queue$MaintainTask.class */
    private static class MaintainTask extends SafeTimerTask {
        private final WeakReference<Queue> queue;

        MaintainTask(Queue queue) {
            this.queue = new WeakReference<>(queue);
        }

        /* JADX WARN: Multi-variable type inference failed */
        private void periodic() {
            Timer.get().scheduleWithFixedDelay(this, 5000L, 5000L, TimeUnit.MILLISECONDS);
        }

        protected void doRun() {
            Queue q = this.queue.get();
            if (q != null) {
                q.maintain();
            } else {
                cancel();
            }
        }
    }

    /* loaded from: Queue$ItemList.class */
    private class ItemList<T extends Item> extends ArrayList<T> {
        private ItemList() {
        }

        public T get(Task task) {
            Iterator<T> it = iterator();
            while (it.hasNext()) {
                T t = (T) it.next();
                if (t.task.equals(task)) {
                    return t;
                }
            }
            return null;
        }

        public List<T> getAll(Task task) {
            ArrayList arrayList = new ArrayList();
            Iterator<T> it = iterator();
            while (it.hasNext()) {
                Item item = (Item) it.next();
                if (item.task.equals(task)) {
                    arrayList.add(item);
                }
            }
            return arrayList;
        }

        public boolean containsKey(Task task) {
            return get(task) != null;
        }

        public T cancel(Task p) {
            T x = get(p);
            if (x != null) {
                x.cancel(Queue.this);
            }
            return x;
        }

        @SuppressFBWarnings(value = {"IA_AMBIGUOUS_INVOCATION_OF_INHERITED_OR_OUTER_METHOD"}, justification = "It will invoke the inherited clear() method according to Java semantics. FindBugs recommends suppressing warnings in such case")
        public void cancelAll() {
            Iterator it = new ArrayList(this).iterator();
            while (it.hasNext()) {
                ((Item) it.next()).cancel(Queue.this);
            }
            clear();
        }
    }

    /* loaded from: Queue$Snapshot.class */
    private static class Snapshot {
        private final Set<WaitingItem> waitingList;
        private final List<BlockedItem> blockedProjects;
        private final List<BuildableItem> buildables;
        private final List<BuildableItem> pendings;

        Snapshot(Set<WaitingItem> waitingList, List<BlockedItem> blockedProjects, List<BuildableItem> buildables, List<BuildableItem> pendings) {
            this.waitingList = new LinkedHashSet(waitingList);
            this.blockedProjects = new ArrayList(blockedProjects);
            this.buildables = new ArrayList(buildables);
            this.pendings = new ArrayList(pendings);
        }

        public String toString() {
            return "Queue.Snapshot{waitingList=" + String.valueOf(this.waitingList) + ";blockedProjects=" + String.valueOf(this.blockedProjects) + ";buildables=" + String.valueOf(this.buildables) + ";pendings=" + String.valueOf(this.pendings) + "}";
        }
    }

    /* loaded from: Queue$LockedRunnable.class */
    private static class LockedRunnable implements Runnable {
        private final Runnable delegate;

        private LockedRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override // java.lang.Runnable
        public void run() {
            Queue.withLock(this.delegate);
        }
    }

    /* loaded from: Queue$BuildableRunnable.class */
    private class BuildableRunnable implements Runnable {
        private final BuildableItem buildableItem;

        private BuildableRunnable(BuildableItem p) {
            this.buildableItem = p;
        }

        @Override // java.lang.Runnable
        public void run() {
            this.buildableItem.enter(Queue.this);
        }
    }

    /* loaded from: Queue$LockedJUCCallable.class */
    private static class LockedJUCCallable<V> implements Callable<V> {
        private final Callable<V> delegate;

        private LockedJUCCallable(Callable<V> delegate) {
            this.delegate = delegate;
        }

        @Override // java.util.concurrent.Callable
        public V call() throws Exception {
            return (V) Queue.withLock(this.delegate);
        }
    }

    /* loaded from: Queue$LockedHRCallable.class */
    private static class LockedHRCallable<V, T extends Throwable> implements hudson.remoting.Callable<V, T> {
        private static final long serialVersionUID = 1;
        private final hudson.remoting.Callable<V, T> delegate;

        private LockedHRCallable(hudson.remoting.Callable<V, T> delegate) {
            this.delegate = delegate;
        }

        public V call() throws Throwable {
            return (V) Queue.withLock(this.delegate);
        }

        public void checkRoles(RoleChecker checker) throws SecurityException {
            this.delegate.checkRoles(checker);
        }
    }

    @CLIResolver
    public static Queue getInstance() {
        return Jenkins.get().getQueue();
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public static void init(Jenkins h) {
        Queue queue = h.getQueue();
        Item[] items = queue.getItems();
        if (items.length > 0) {
            LOGGER.warning(() -> {
                return "Loading queue will discard previously scheduled items: " + Arrays.toString(items);
            });
        }
        queue.load();
    }

    @Extension
    @Restricted({NoExternalUse.class})
    /* loaded from: Queue$Saver.class */
    public static final class Saver extends QueueListener implements Runnable {

        @VisibleForTesting
        static int DELAY_SECONDS = SystemProperties.getInteger("hudson.model.Queue.Saver.DELAY_SECONDS", 60).intValue();
        private final Object lock = new Object();

        @GuardedBy("lock")
        private Future<?> nextSave;

        public void onEnterWaiting(WaitingItem wi) {
            push();
        }

        public void onLeft(LeftItem li) {
            push();
        }

        private void push() {
            if (DELAY_SECONDS < 0) {
                return;
            }
            synchronized (this.lock) {
                if (this.nextSave == null || this.nextSave.isDone() || this.nextSave.isCancelled()) {
                    this.nextSave = Timer.get().schedule(this, DELAY_SECONDS, TimeUnit.SECONDS);
                }
            }
        }

        @Override // java.lang.Runnable
        public void run() {
            try {
                Jenkins j = Jenkins.getInstanceOrNull();
                if (j != null) {
                    j.getQueue().save();
                }
                synchronized (this.lock) {
                    this.nextSave = null;
                }
            } catch (Throwable th) {
                synchronized (this.lock) {
                    this.nextSave = null;
                    throw th;
                }
            }
        }

        @VisibleForTesting
        @NonNull
        @Restricted({NoExternalUse.class})
        Future<?> getNextSave() {
            hudson.remoting.Future future;
            synchronized (this.lock) {
                if (this.nextSave == null) {
                    future = Futures.precomputed((Object) null);
                } else {
                    future = this.nextSave;
                }
            }
            return future;
        }
    }
}
