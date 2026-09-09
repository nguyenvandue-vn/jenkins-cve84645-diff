package org.kohsuke.stapler.bind;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.WebApp;

/* loaded from: Bound.class */
public abstract class Bound implements HttpResponse {
    public abstract void release();

    public abstract String getURL();

    public abstract Object getTarget();

    public final String getProxyScript() {
        return getProxyScript(getURL(), getTarget().getClass());
    }

    public static String getProxyScriptURL(String variableName, Bound bound) {
        if (bound == null) {
            return Stapler.getCurrentRequest2().getContextPath() + "/$stapler/bound/script/null?var=" + variableName;
        }
        return bound.getProxyScriptURL(variableName);
    }

    public final String getProxyScriptURL(String variableName) {
        String methodsList = String.join(",", getBoundJavaScriptUrlNames(getTarget().getClass()));
        return Stapler.getCurrentRequest2().getContextPath() + "/$stapler/bound/script" + getURL() + "?var=" + variableName + "&methods=" + methodsList;
    }

    private static Set<String> getBoundJavaScriptUrlNames(Class<?> clazz) {
        Set<String> names = new HashSet<>();
        for (Method m : clazz.getMethods()) {
            if (m.getName().startsWith("js")) {
                names.add(camelize(m.getName().substring(2)));
            } else {
                JavaScriptMethod a = m.getAnnotation(JavaScriptMethod.class);
                if (a != null) {
                    if (a.name().length == 0) {
                        names.add(m.getName());
                    } else {
                        names.addAll(Arrays.asList(a.name()));
                    }
                }
            }
        }
        return names;
    }

    public final Set<String> getBoundJavaScriptUrlNames() {
        return getBoundJavaScriptUrlNames(getTarget().getClass());
    }

    public static String getProxyScript(String url, Class<?> clazz) {
        return getProxyScript(url, (String[]) getBoundJavaScriptUrlNames(clazz).toArray(x$0 -> {
            return new String[x$0];
        }));
    }

    public static String getProxyScript(String url, String[] methods) {
        String crumb = WebApp.getCurrent().getCrumbIssuer().getCrumbExpression();
        String methodNamesList = (String) Arrays.stream(methods).sorted().map(it -> {
            return "'" + escapeQuotedString(it) + "'";
        }).collect(Collectors.joining(","));
        return "makeStaplerProxy('" + escapeQuotedString(url) + "'," + crumb + ",[" + methodNamesList + "])";
    }

    private static String escapeQuotedString(String singleQuotedJsValue) {
        return singleQuotedJsValue.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String camelize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
