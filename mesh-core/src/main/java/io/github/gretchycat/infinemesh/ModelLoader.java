package io.github.gretchycat.infinemesh;
import java.io.*;
import java.util.*;

public final class ModelLoader {
    private final List<ModelDecoder> decoders;
    public ModelLoader() { this(Arrays.asList(new StlDecoder(),new GcodeDecoder())); }
    public ModelLoader(List<ModelDecoder> decoders) { this.decoders=Collections.unmodifiableList(new ArrayList<>(decoders)); }
    /** Caller owns the input stream. Decode budgets are enforced before parsing. */
    public ModelDocument load(InputStream stream, String name, String mime, DecodeLimits limits) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
        while((n=stream.read(buffer))!=-1) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Load cancelled");
            if((long)out.size()+n>limits.maxBytes()) throw new IOException("File exceeds the configured " + limits.maxBytes()/1024/1024 + " MiB preview limit");
            out.write(buffer,0,n);
        }
        byte[] data=out.toByteArray(), prefix=Arrays.copyOf(data,Math.min(data.length,512));
        for(ModelDecoder decoder:decoders) if(decoder.supports(name==null?"":name,mime==null?"":mime,prefix)) return decoder.decode(data,limits);
        throw new IOException("Unsupported model format. This version opens STL and G-code.");
    }
}
