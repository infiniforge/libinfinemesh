package io.github.gretchycat.infinemesh;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ModelThumbnailTest {
    private ModelDocument mesh(float[] p) { return new ModelDocument("STL", new MeshGeometry(p), null, List.of()); }
    @Test public void meshHasTransparentMarginsAndVisibleFlatFaces() throws Exception {
        ModelThumbnail.Raster r=ModelThumbnail.render(mesh(new float[]{0,0,0,1,0,0,0,1,0}),128);
        assertEquals(128,r.width()); assertEquals(128,r.height());
        int count=0; for(int p:r.argb()) if(p!=0) count++;
        assertTrue(count>500); assertTrue(count<128*128/2); assertEquals(0,r.argb()[0]);
        int[] copy=r.argb(); copy[0]=-1; assertEquals(0,r.argb()[0]);
    }
    @Test public void overlappingFacesAreIndependentOfTriangleOrder() throws Exception {
        float[] back={0,0,0,1,0,0,0,1,0};
        float[] front={.2f,.2f,.2f,1.3f,.3f,.3f,.4f,1.4f,.4f};
        float[] a=new float[18],b=new float[18];
        System.arraycopy(back,0,a,0,9);System.arraycopy(front,0,a,9,9);
        System.arraycopy(front,0,b,0,9);System.arraycopy(back,0,b,9,9);
        assertArrayEquals(ModelThumbnail.render(mesh(a),96).argb(),ModelThumbnail.render(mesh(b),96).argb());
    }
    @Test public void toolpathIncludesTravelAndPrintedSegmentsDespiteUnknownCodes() throws Exception {
        var doc=new GcodeDecoder().decode("G1 X20 F600\nG999 X900\nG1 Y20 E1\n".getBytes(StandardCharsets.US_ASCII),DecodeLimits.DEFAULT);
        var raster=ModelThumbnail.render(doc,128);
        boolean travel=false,printed=false;
        for(int pixel:raster.argb()) {travel|=pixel==0xff899cac;printed|=pixel==0xff56d8cf;}
        assertTrue(travel);assertTrue(printed);
    }
    @Test public void sizeAndCancellationAreEnforced() throws Exception {
        var doc=mesh(new float[]{0,0,0,1,0,0,0,1,0});
        assertThrows(IllegalArgumentException.class,()->ModelThumbnail.render(doc,513));
        assertThrows(IllegalArgumentException.class,()->ModelThumbnail.render(doc,0));
        assertThrows(java.util.concurrent.CancellationException.class,()->ModelThumbnail.render(doc,32,()->{throw new java.util.concurrent.CancellationException();}));
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class,()->ModelThumbnail.render(doc,32)); }
        finally {Thread.interrupted();}
    }
}
