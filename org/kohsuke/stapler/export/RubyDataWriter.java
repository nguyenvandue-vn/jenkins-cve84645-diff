package org.kohsuke.stapler.export;

import java.io.IOException;
import java.io.Writer;

/* loaded from: RubyDataWriter.class */
final class RubyDataWriter extends JSONDataWriter {
    RubyDataWriter(Writer out, ExportConfig config) throws IOException {
        super(out, config);
    }

    @Override // org.kohsuke.stapler.export.JSONDataWriter
    public void name(String name) throws IOException {
        comma();
        this.out.write("\"" + escape(name) + "\" => ");
        this.needComma = false;
    }

    @Override // org.kohsuke.stapler.export.JSONDataWriter
    public void valueNull() throws IOException {
        data("nil");
    }

    @Override // org.kohsuke.stapler.export.JSONDataWriter
    protected void _startObject() throws IOException {
        comma();
        this.needComma = false;
        this.out.write("OpenStruct.new({");
    }

    @Override // org.kohsuke.stapler.export.JSONDataWriter
    public void endObject() throws IOException {
        this.out.write("})");
        this.needComma = true;
    }
}
