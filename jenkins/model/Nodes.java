package jenkins.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.PersistenceRoot;
import hudson.model.Queue;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.slaves.EphemeralNode;
import hudson.slaves.OfflineCause;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted({NoExternalUse.class})
/* loaded from: Nodes.class */
public class Nodes implements PersistenceRoot {
    private static final Logger LOGGER = Logger.getLogger(Nodes.class.getName());

    @Restricted({NoExternalUse.class})
    private static final boolean ENFORCE_NAME_RESTRICTIONS = SystemProperties.getBoolean(Nodes.class.getName() + ".enforceNameRestrictions", true);

    /* renamed from: jenkins, reason: collision with root package name */
    @NonNull
    private final Jenkins f0jenkins;
    private final ConcurrentMap<String, Node> nodes = new ConcurrentSkipListMap();

    Nodes(@NonNull Jenkins jenkins2) {
        this.f0jenkins = jenkins2;
    }

    @NonNull
    public List<Node> getNodes() {
        return new ArrayList(this.nodes.values());
    }

    public void setNodes(@NonNull final Collection<? extends Node> nodes) throws IOException {
        Map<String, Node> toRemove = new HashMap<>();
        Queue.withLock(() -> {
            toRemove.putAll(this.nodes);
            Iterator it = nodes.iterator();
            while (it.hasNext()) {
                Node node = (Node) it.next();
                String name = node.getNodeName();
                this.nodes.put(name, node);
                node.onLoad(this, name);
                Node oldNode = (Node) toRemove.get(name);
                if (oldNode != null) {
                    node.setTemporaryOfflineCause(oldNode.getTemporaryOfflineCause());
                    NodeListener.fireOnUpdated(oldNode, node);
                    toRemove.remove(name);
                } else {
                    NodeListener.fireOnCreated(node);
                }
            }
            this.nodes.keySet().removeAll(toRemove.keySet());
            this.f0jenkins.updateComputerList();
            this.f0jenkins.trimLabels();
        });
        save();
        for (Node deletedNode : toRemove.values()) {
            NodeListener.fireOnDeleted(deletedNode);
            String nodeName = deletedNode.getNodeName();
            LOGGER.fine(() -> {
                return "deleting " + String.valueOf(new File(getRootDir(), nodeName));
            });
            Util.deleteRecursive(new File(getRootDir(), nodeName));
        }
    }

    public boolean addNodeIfAbsent(@NonNull final Node node) throws IOException {
        if (ENFORCE_NAME_RESTRICTIONS) {
            Jenkins.checkGoodName(node.getNodeName());
        }
        Node old = this.nodes.putIfAbsent(node.getNodeName(), node);
        if (old == null) {
            handleAddedNode(node, null);
            return true;
        }
        return false;
    }

    public void addNode(@NonNull final Node node) throws IOException {
        if (ENFORCE_NAME_RESTRICTIONS) {
            Jenkins.checkGoodName(node.getNodeName());
        }
        Node old = this.nodes.put(node.getNodeName(), node);
        if (node != old) {
            handleAddedNode(node, old);
        }
    }

    private void handleAddedNode(@NonNull final Node node, final Node old) throws IOException {
        node.onLoad(this, node.getNodeName());
        this.f0jenkins.updateNewComputer(node);
        this.f0jenkins.trimLabels(node, old);
        try {
            node.save();
            if (old != null) {
                NodeListener.fireOnUpdated(old, node);
            } else {
                NodeListener.fireOnCreated(node);
            }
        } catch (IOException | RuntimeException e) {
            Queue.runWithLock(() -> {
                this.nodes.compute(node.getNodeName(), (ignoredNodeName, ignoredNode) -> {
                    return old;
                });
                this.f0jenkins.updateComputers(node);
                if (old != null) {
                    this.f0jenkins.trimLabels(node, old);
                } else {
                    this.f0jenkins.trimLabels(node);
                }
            });
            throw e;
        }
    }

    public XmlFile getConfigFile(Node node) {
        return getConfigFile(node.getRootDir());
    }

    public XmlFile getConfigFile(File dir) {
        return new XmlFile(Jenkins.XSTREAM, new File(dir, "config.xml"));
    }

    public XmlFile getConfigFile(String nodeName) {
        return new XmlFile(Jenkins.XSTREAM, new File(getRootDir(), nodeName + File.separator + "config.xml"));
    }

    public boolean updateNode(@NonNull final Node node) throws IOException {
        return updateNode(node, true);
    }

    private boolean updateNode(@NonNull final Node node, boolean fireListener) throws IOException {
        boolean exists;
        try {
            exists = ((Boolean) Queue.withLock(() -> {
                if (node == this.nodes.get(node.getNodeName())) {
                    this.f0jenkins.trimLabels(node);
                    return true;
                }
                return false;
            })).booleanValue();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e2) {
            exists = false;
        }
        if (exists) {
            node.save();
            if (fireListener) {
                NodeListener.fireOnUpdated(node, node);
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean replaceNode(final Node oldOne, @NonNull final Node newOne) throws IOException {
        if (ENFORCE_NAME_RESTRICTIONS) {
            Jenkins.checkGoodName(newOne.getNodeName());
        }
        if (oldOne == this.nodes.get(oldOne.getNodeName())) {
            Queue.runWithLock(() -> {
                this.nodes.remove(oldOne.getNodeName());
                this.nodes.put(newOne.getNodeName(), newOne);
                newOne.onLoad(this, newOne.getNodeName());
            });
            updateNode(newOne, false);
            if (!newOne.getNodeName().equals(oldOne.getNodeName())) {
                LOGGER.fine(() -> {
                    return "deleting " + String.valueOf(new File(getRootDir(), oldOne.getNodeName()));
                });
                Util.deleteRecursive(new File(getRootDir(), oldOne.getNodeName()));
            }
            Queue.withLock(() -> {
                this.f0jenkins.updateComputers(newOne);
                this.f0jenkins.trimLabels(oldOne, newOne);
            });
            NodeListener.fireOnUpdated(oldOne, newOne);
            return true;
        }
        return false;
    }

    public void removeNode(@NonNull final Node node) throws IOException {
        if (node == this.nodes.get(node.getNodeName())) {
            AtomicBoolean match = new AtomicBoolean();
            Queue.runWithLock(() -> {
                Computer c = node.toComputer();
                if (c != null) {
                    c.recordTermination();
                    c.disconnect(OfflineCause.create(Messages._Hudson_NodeBeingRemoved()));
                }
                match.set(node == this.nodes.remove(node.getNodeName()));
            });
            LOGGER.fine(() -> {
                return "deleting " + String.valueOf(new File(getRootDir(), node.getNodeName()));
            });
            Util.deleteRecursive(new File(getRootDir(), node.getNodeName()));
            if (match.get()) {
                this.f0jenkins.updateComputers(node);
                this.f0jenkins.trimLabels(node);
            }
            NodeListener.fireOnDeleted(node);
            SaveableListener.fireOnDeleted(node, getConfigFile(node));
        }
    }

    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        for (Node n : this.nodes.values()) {
            if (!(n instanceof EphemeralNode)) {
                XmlFile xmlFile = getConfigFile(n);
                LOGGER.fine(() -> {
                    return "saving " + String.valueOf(xmlFile);
                });
                xmlFile.write(n);
                SaveableListener.fireOnChange(this, xmlFile);
            }
        }
    }

    @CheckForNull
    public Node getNode(String name) {
        if (name == null) {
            return null;
        }
        return this.nodes.get(name);
    }

    public void load() throws IOException {
        File nodesDir = getRootDir();
        File[] subdirs = nodesDir.listFiles((v0) -> {
            return v0.isDirectory();
        });
        Map<String, Node> newNodes = new TreeMap<>();
        if (subdirs != null) {
            for (File subdir : subdirs) {
                try {
                    load(subdir, newNodes);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "could not load " + String.valueOf(subdir), (Throwable) e);
                }
            }
        }
        Queue.runWithLock(() -> {
            newNodes.entrySet().removeIf(stringNodeEntry -> {
                return ExtensionList.lookup(NodeListener.class).stream().anyMatch(nodeListener -> {
                    if (!nodeListener.allowLoad((Node) stringNodeEntry.getValue())) {
                        LOGGER.log(Level.FINE, () -> {
                            return "Loading of node " + ((String) stringNodeEntry.getKey()) + " vetoed by " + String.valueOf(nodeListener);
                        });
                        return true;
                    }
                    return false;
                });
            });
            this.nodes.entrySet().removeIf(stringNodeEntry2 -> {
                return !(stringNodeEntry2.getValue() instanceof EphemeralNode);
            });
            this.nodes.putAll(newNodes);
            this.f0jenkins.updateComputerList();
            this.f0jenkins.trimLabels();
        });
    }

    @CheckForNull
    public Node getOrLoad(String name) {
        Node node = getNode(name);
        LOGGER.fine(() -> {
            return "already loaded? " + String.valueOf(node);
        });
        return node == null ? load(name) : node;
    }

    @CheckForNull
    public Node load(String name) {
        try {
            XmlFile xmlFile = getConfigFile(name);
            if (xmlFile.exists()) {
                Node n = (Node) xmlFile.read();
                this.nodes.put(n.getNodeName(), n);
                n.onLoad(this, n.getNodeName());
                this.f0jenkins.updateNewComputer(n);
                this.f0jenkins.trimLabels(n);
                LOGGER.finer(() -> {
                    return "loading " + String.valueOf(xmlFile);
                });
                return n;
            }
            LOGGER.fine(() -> {
                return "no such file " + String.valueOf(xmlFile);
            });
            return null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not load " + name, (Throwable) e);
            return null;
        }
    }

    private Node load(File dir, Map<String, Node> nodesCollector) throws IOException {
        Node n = (Node) getConfigFile(dir).read();
        if (n != null) {
            nodesCollector.put(n.getNodeName(), n);
            n.onLoad(this, n.getNodeName());
        }
        return n;
    }

    public void load(File dir) throws IOException {
        Node n = load(dir, this.nodes);
        this.f0jenkins.updateComputers(n);
        this.f0jenkins.trimLabels(n);
    }

    public void unload(Node node) {
        if (node == this.nodes.get(node.getNodeName())) {
            AtomicBoolean match = new AtomicBoolean();
            Queue.withLock(() -> {
                match.set(node == this.nodes.remove(node.getNodeName()));
            });
            if (match.get()) {
                this.f0jenkins.updateComputers(node);
                this.f0jenkins.trimLabels(node);
            }
        }
    }

    public boolean isLegacy() {
        return !getRootDir().isDirectory();
    }

    public File getRootDir() {
        return new File(this.f0jenkins.getRootDir(), "nodes");
    }

    public File getRootDirFor(Node node) {
        return getRootDirFor(node.getNodeName());
    }

    private File getRootDirFor(String name) {
        return new File(getRootDir(), name);
    }

    @Extension
    /* loaded from: Nodes$ScheduleMaintenanceAfterSavingNode.class */
    public static class ScheduleMaintenanceAfterSavingNode extends SaveableListener {
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Node) {
                Jenkins.get().getQueue().m44scheduleMaintenance();
            }
        }
    }
}
