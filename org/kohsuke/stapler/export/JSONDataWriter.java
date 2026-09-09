package org.kohsuke.stapler.export;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Arrays;

/* loaded from: JSONDataWriter.class */
class JSONDataWriter implements DataWriter {
    protected boolean needComma;
    protected final Writer out;
    protected final ExportConfig config;
    private int indent;
    private String classAttr;
    private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] INDENT = new char[32];

    JSONDataWriter(Writer out, ExportConfig config) throws IOException {
        this.out = out;
        this.config = config;
        this.indent = config.isPrettyPrint() ? 0 : -1;
    }

    @NonNull
    public ExportConfig getExportConfig() {
        return this.config;
    }

    public void name(String name) throws IOException {
        comma();
        if (this.indent < 0) {
            this.out.write("\"" + escape(name) + "\":");
        } else {
            this.out.write("\"" + escape(name) + "\" : ");
        }
        this.needComma = false;
    }

    protected void data(String v) throws IOException {
        comma();
        this.out.write(v);
    }

    protected void comma() throws IOException {
        if (this.needComma) {
            this.out.write(44);
            indent();
        }
        this.needComma = true;
    }

    private void indent() throws IOException {
        if (this.indent >= 0) {
            this.out.write(10);
            int i = this.indent * 2;
            while (true) {
                int i2 = i;
                if (i2 > 0) {
                    int len = Math.min(i2, INDENT.length);
                    this.out.write(INDENT, 0, len);
                    i = i2 - len;
                } else {
                    return;
                }
            }
        }
    }

    private void inc() {
        if (this.indent < 0) {
            return;
        }
        this.indent++;
    }

    private void dec() {
        if (this.indent < 0) {
            return;
        }
        this.indent--;
    }

    public void valuePrimitive(Object v) throws IOException {
        data(v.toString());
    }

    public void value(String v) throws IOException {
        data("\"" + escape(v) + "\"");
    }

    protected static String escape(String v) {
        StringBuilder buf = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isISOControl(c) || Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                buf.append("\\u");
                buf.append(HEX[(c >> '\f') & 15]);
                buf.append(HEX[(c >> '\b') & 15]);
                buf.append(HEX[(c >> 4) & 15]);
                buf.append(HEX[c & 15]);
            } else {
                switch (c) {
                    case '\t':
                        buf.append("\\t");
                        break;
                    case '\n':
                        buf.append("\\n");
                        break;
                    case '\r':
                        buf.append("\\r");
                        break;
                    case '\"':
                        buf.append("\\\"");
                        break;
                    case '\\':
                        buf.append("\\\\");
                        break;
                    default:
                        buf.append(c);
                        break;
                }
            }
        }
        return buf.toString();
    }

    public void valueNull() throws IOException {
        data("null");
    }

    private void open(char symbol) throws IOException {
        comma();
        this.out.write(symbol);
        this.needComma = false;
        inc();
        indent();
    }

    private void close(char symbol) throws IOException {
        dec();
        indent();
        this.needComma = true;
        this.out.write(symbol);
    }

    public void startArray() throws IOException {
        open('[');
    }

    public void endArray() throws IOException {
        close(']');
    }

    public void type(Type expected, Class actual) throws IOException {
        this.classAttr = this.config.getClassAttribute().print(expected, actual);
    }

    public void startObject() throws IOException {
        _startObject();
        if (this.classAttr != null) {
            name("_class");
            value(this.classAttr);
            this.classAttr = null;
        }
    }

    protected void _startObject() throws IOException {
        open('{');
    }

    public void endObject() throws IOException {
        close('}');
    }

    static {
        Arrays.fill(INDENT, ' ');
    }
}
