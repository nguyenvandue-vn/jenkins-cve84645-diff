package hudson.model;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.diagnosis.OldDataMonitor;
import hudson.tasks.Maven;
import hudson.util.XStream2;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.util.NonLocalizable;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = Maven.MavenInstallation.MAVEN_30)
/* loaded from: HealthReport.class */
public class HealthReport implements Serializable, Comparable<HealthReport> {
    private static final String HEALTH_OVER_80 = "icon-health-80plus";
    private static final String HEALTH_61_TO_80 = "icon-health-60to79";
    private static final String HEALTH_41_TO_60 = "icon-health-40to59";
    private static final String HEALTH_21_TO_40 = "icon-health-20to39";
    private static final String HEALTH_0_TO_20 = "icon-health-00to19";
    private static final String HEALTH_OVER_80_IMG = "health-80plus.png";
    private static final String HEALTH_61_TO_80_IMG = "health-60to79.png";
    private static final String HEALTH_41_TO_60_IMG = "health-40to59.png";
    private static final String HEALTH_21_TO_40_IMG = "health-20to39.png";
    private static final String HEALTH_0_TO_20_IMG = "health-00to19.png";
    private static final String HEALTH_UNKNOWN_IMG = "empty.png";
    private static final Map<String, String> iconIMGToClassMap = new HashMap();
    private static final long serialVersionUID = 7451361788415642230L;
    private int score;
    private String iconClassName;
    private String iconUrl;

    @Deprecated
    private transient String description;
    private Localizable localizibleDescription;

    static {
        iconIMGToClassMap.put(HEALTH_OVER_80_IMG, HEALTH_OVER_80);
        iconIMGToClassMap.put(HEALTH_61_TO_80_IMG, HEALTH_61_TO_80);
        iconIMGToClassMap.put(HEALTH_41_TO_60_IMG, HEALTH_41_TO_60);
        iconIMGToClassMap.put(HEALTH_21_TO_40_IMG, HEALTH_21_TO_40);
        iconIMGToClassMap.put(HEALTH_0_TO_20_IMG, HEALTH_0_TO_20);
    }

    @Deprecated
    public HealthReport(int score, String iconUrl, String description) {
        this(score, iconUrl, (Localizable) new NonLocalizable(description));
    }

    public HealthReport(int score, String iconUrl, Localizable description) {
        this.score = score;
        if (score <= 20) {
            this.iconClassName = HEALTH_0_TO_20;
        } else if (score <= 40) {
            this.iconClassName = HEALTH_21_TO_40;
        } else if (score <= 60) {
            this.iconClassName = HEALTH_41_TO_60;
        } else if (score <= 80) {
            this.iconClassName = HEALTH_61_TO_80;
        } else {
            this.iconClassName = HEALTH_OVER_80;
        }
        if (iconUrl == null) {
            if (score <= 20) {
                this.iconUrl = HEALTH_0_TO_20_IMG;
            } else if (score <= 40) {
                this.iconUrl = HEALTH_21_TO_40_IMG;
            } else if (score <= 60) {
                this.iconUrl = HEALTH_41_TO_60_IMG;
            } else if (score <= 80) {
                this.iconUrl = HEALTH_61_TO_80_IMG;
            } else {
                this.iconUrl = HEALTH_OVER_80_IMG;
            }
        } else {
            this.iconUrl = iconUrl;
        }
        this.description = null;
        setLocalizibleDescription(description);
    }

    @Deprecated
    public HealthReport(int score, String description) {
        this(score, (String) null, description);
    }

    public HealthReport(int score, Localizable description) {
        this(score, (String) null, description);
    }

    public HealthReport() {
        this(100, HEALTH_UNKNOWN_IMG, Messages._HealthReport_EmptyString());
    }

    @Exported
    public int getScore() {
        return this.score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    @Exported
    public String getIconUrl() {
        return this.iconUrl;
    }

    @Exported
    public String getIconClassName() {
        return this.iconClassName;
    }

    public String getIconUrl(String size) {
        if (this.iconUrl == null) {
            return Jenkins.RESOURCE_PATH + "/images/" + size + "/empty.png";
        }
        if (this.iconUrl.startsWith("/")) {
            return this.iconUrl.replace("/32x32/", "/" + size + "/");
        }
        return Jenkins.RESOURCE_PATH + "/images/" + size + "/" + this.iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    @Exported
    public String getDescription() {
        return getLocalizableDescription().toString();
    }

    public void setDescription(String description) {
        setLocalizibleDescription(new NonLocalizable(description));
    }

    public Localizable getLocalizableDescription() {
        return this.localizibleDescription;
    }

    public void setLocalizibleDescription(Localizable localizibleDescription) {
        this.localizibleDescription = localizibleDescription;
    }

    public List<HealthReport> getAggregatedReports() {
        return Collections.emptyList();
    }

    public boolean isAggregateReport() {
        return false;
    }

    @Override // java.lang.Comparable
    public int compareTo(HealthReport o) {
        return Integer.compare(this.score, o.score);
    }

    public static HealthReport min(HealthReport a, HealthReport b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b != null && a.compareTo(b) > 0) {
            return b;
        }
        return a;
    }

    public static HealthReport max(HealthReport a, HealthReport b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b != null && a.compareTo(b) < 0) {
            return b;
        }
        return a;
    }

    /* loaded from: HealthReport$ConverterImpl.class */
    public static class ConverterImpl extends XStream2.PassthruConverter<HealthReport> {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // hudson.util.XStream2.PassthruConverter
        public void callback(HealthReport hr, UnmarshallingContext context) {
            if (hr.localizibleDescription == null) {
                hr.localizibleDescription = new NonLocalizable(hr.description == null ? "" : hr.description);
                OldDataMonitor.report(context, "1.256");
            }
            if (hr.iconClassName == null && hr.iconUrl != null && HealthReport.iconIMGToClassMap.containsKey(hr.iconUrl)) {
                hr.iconClassName = HealthReport.iconIMGToClassMap.get(hr.iconUrl);
            }
        }
    }
}
