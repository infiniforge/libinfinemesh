package io.github.gretchycat.infinemesh;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class StlDecoder implements ModelDecoder {
    public boolean supports(String name,String mime,byte[] prefix) {
        String kind=ModelFormats.mimeType(name,mime);
        return "model/stl".equals(kind) || (kind==null &&
            new String(prefix,StandardCharsets.US_ASCII).trim().startsWith("solid"));
    }
    public ModelDocument decode(byte[] data,DecodeLimits limits) throws IOException {
        // A binary STL header may itself start with 'solid'. Exact byte count wins.
        if(data.length>=84) {
            long count=Integer.toUnsignedLong(ByteBuffer.wrap(data,80,4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            if(84+count*50==data.length) return binary(data,count,limits);
        }
        String prefix=new String(data,0,Math.min(data.length,80),StandardCharsets.US_ASCII).trim();
        if(!prefix.startsWith("solid")) {
            if(data.length<84) throw new IOException("Truncated binary STL header");
            long count=Integer.toUnsignedLong(ByteBuffer.wrap(data,80,4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            return binary(data,count,limits);
        }
        return ascii(data,limits);
    }
    private ModelDocument binary(byte[] data,long count,DecodeLimits limits) throws IOException {
        if(count<1 || count>limits.maxTriangles()) throw new IOException("STL triangle count exceeds preview limits or is empty");
        if(84+count*50!=data.length) throw new IOException("Binary STL length does not match its triangle count");
        float[] points=new float[(int)count*9]; ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); b.position(84);
        for(int i=0;i<count;i++) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Load cancelled");
            b.position(b.position()+12);
            for(int v=0;v<9;v++) { float f=b.getFloat(); if(!Float.isFinite(f)) throw new IOException("STL contains non-finite coordinates"); points[i*9+v]=f; }
            b.getShort();
        }
        return new ModelDocument("Binary STL",new MeshGeometry(points),null,Collections.emptyList());
    }
    private ModelDocument ascii(byte[] data,DecodeLimits limits) throws IOException {
        FloatBuilder points=new FloatBuilder();
        try(BufferedReader reader=new BufferedReader(new StringReader(new String(data,StandardCharsets.US_ASCII)))) {
            String line; int state=0, vertices=0; boolean solid=false, closed=false;
            while((line=reader.readLine())!=null) {
                if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Load cancelled");
                String t=line.trim(); if(t.isEmpty()) continue;
                String[] words=t.split("\\s+");
                switch(words[0]) {
                    case "solid" -> { if(state!=0 || solid) throw new IOException("Unexpected solid in STL"); solid=true; closed=false; }
                    case "facet" -> { if(!solid || state!=0 || words.length!=5 || !words[1].equals("normal")) throw new IOException("Invalid STL facet"); state=1; vertices=0; }
                    case "outer" -> { if(state!=1 || !t.matches("outer\\s+loop")) throw new IOException("Invalid STL loop"); state=2; }
                    case "vertex" -> {
                        if(state!=2 || vertices==3 || words.length!=4) throw new IOException("Invalid STL vertex");
                        for(int a=1;a<4;a++) { float f=Float.parseFloat(words[a]); if(!Float.isFinite(f)) throw new IOException("STL contains non-finite coordinates"); points.add(f); }
                        vertices++;
                    }
                    case "endloop" -> { if(state!=2 || vertices!=3) throw new IOException("Incomplete STL triangle"); state=3; }
                    case "endfacet" -> { if(state!=3) throw new IOException("Incomplete STL facet"); state=0; if(points.size()/9>limits.maxTriangles()) throw new IOException("STL exceeds preview triangle limit"); }
                    case "endsolid" -> { if(state!=0 || !solid) throw new IOException("Incomplete STL solid"); solid=false; closed=true; }
                    default -> throw new IOException("Unexpected ASCII STL token: "+words[0]);
                }
            }
            if(state!=0 || !closed || solid || points.size()==0) throw new IOException("Incomplete or empty ASCII STL");
        } catch(NumberFormatException e) { throw new IOException("Invalid STL number",e); }
        return new ModelDocument("ASCII STL",new MeshGeometry(points.array()),null,Collections.emptyList());
    }
}
