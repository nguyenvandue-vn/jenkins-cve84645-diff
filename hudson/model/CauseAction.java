package hudson.model;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Queue;
import hudson.model.queue.FoldableAction;
import hudson.tasks.Maven;
import hudson.util.XStream2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.RunAction2;
import jenkins.security.XStreamDeserializable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
/* loaded from: CauseAction.class */
public class CauseAction implements FoldableAction, RunAction2 {

    @XStreamDeserializable
    @Deprecated
    private transient Cause cause;

    @XStreamDeserializable
    @Deprecated
    private transient List<Cause> causes;
    private Map<Cause, Integer> causeBag;

    public CauseAction(Cause c) {
        this.causeBag = new LinkedHashMap();
        this.causeBag.put(c, 1);
    }

    private void addCause(Cause c) {
        synchronized (this.causeBag) {
            this.causeBag.compute(c, (unused, cnt) -> {
                return Integer.valueOf(cnt == null ? 1 : cnt.intValue() + 1);
            });
        }
    }

    private void addCauses(Collection<? extends Cause> causes) {
        for (Cause cause : causes) {
            addCause(cause);
        }
    }

    public CauseAction(Cause... c) {
        this(Arrays.asList(c));
    }

    public CauseAction(Collection<? extends Cause> causes) {
        this.causeBag = new LinkedHashMap();
        addCauses(causes);
    }

    public CauseAction(CauseAction ca) {
        this.causeBag = new LinkedHashMap();
        addCauses(ca.getCauses());
    }

    @Exported(visibility = Maven.MavenInstallation.MAVEN_30)
    public List<Cause> getCauses() {
        List<Cause> r = new ArrayList<>();
        for (Map.Entry<Cause, Integer> entry : this.causeBag.entrySet()) {
            r.addAll(Collections.nCopies(entry.getValue().intValue(), entry.getKey()));
        }
        return Collections.unmodifiableList(r);
    }

    public <T extends Cause> T findCause(Class<T> type) {
        for (Cause c : this.causeBag.keySet()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    public String getDisplayName() {
        return "Cause";
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "cause";
    }

    public Map<Cause, Integer> getCauseCounts() {
        return Collections.unmodifiableMap(this.causeBag);
    }

    @Deprecated
    public String getShortDescription() {
        if (this.causeBag.isEmpty()) {
            return "N/A";
        }
        return this.causeBag.keySet().iterator().next().getShortDescription();
    }

    public void onLoad(Run<?, ?> owner) {
        for (Cause c : this.causeBag.keySet()) {
            if (c != null) {
                c.onLoad(owner);
            }
        }
    }

    public void onAttached(Run<?, ?> owner) {
        for (Cause c : this.causeBag.keySet()) {
            if (c != null) {
                c.onAddedTo(owner);
            }
        }
    }

    public void foldIntoExisting(Queue.Item item, Queue.Task owner, List<Action> otherActions) {
        CauseAction existing = item.getAction(CauseAction.class);
        if (existing != null) {
            existing.addCauses(getCauses());
        } else {
            item.addAction(new CauseAction(this));
        }
    }

    /* loaded from: CauseAction$ConverterImpl.class */
    public static class ConverterImpl extends XStream2.PassthruConverter<CauseAction> {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // hudson.util.XStream2.PassthruConverter
        public void callback(CauseAction ca, UnmarshallingContext context) {
            if (ca.cause != null) {
                if (ca.causeBag == null) {
                    ca.causeBag = new LinkedHashMap();
                }
                ca.addCause(ca.cause);
                OldDataMonitor.report(context, "1.288");
                ca.cause = null;
                return;
            }
            if (ca.causes != null) {
                if (ca.causeBag == null) {
                    ca.causeBag = new LinkedHashMap();
                }
                ca.addCauses(ca.causes);
                OldDataMonitor.report(context, "1.653");
                ca.causes = null;
            }
        }
    }
}
