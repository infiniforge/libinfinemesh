package io.github.gretchycat.infinemesh;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;

/** Bounded, CPU-rendered isometric previews. No GL context, file paths, or source writes. */
public final class ModelThumbnail {
    private ModelThumbnail() {}
    public record Raster(int width, int height, int[] argb) {
        public Raster { argb = argb.clone(); }
        @Override public int[] argb() { return argb.clone(); }
    }
    public static Raster render(ModelDocument document, int pixels) throws IOException {
        return render(document, pixels, () -> {});
    }
    /** The callback lets stream hosts propagate cancellation during rasterization. */
    public static Raster render(ModelDocument document, int pixels, Runnable cancellation) throws IOException {
        if (pixels < 1 || pixels > 512) throw new IllegalArgumentException("Thumbnail size must be 1..512");
        return new Painter(document, pixels, cancellation).draw();
    }
    private static final class Painter {
        final ModelDocument doc;
        final int size;
        final int[] colors;
        final double[] depth;
        final Runnable cancellation;
        final Bounds bounds;
        final double extent;
        long work;
        Painter(ModelDocument doc, int size, Runnable cancellation) {
            this.doc=doc; this.size=size; this.cancellation=cancellation; bounds=doc.bounds();
            extent=Math.max(bounds.extent(), 1e-30);
            colors=new int[size*size]; depth=new double[colors.length]; Arrays.fill(depth,Double.NEGATIVE_INFINITY);
        }
        void check() throws IOException {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Thumbnail cancelled");
            cancellation.run();
        }
        double[] project(float[] xyz, int offset) {
            double x=(xyz[offset]- (double)bounds.center(0))/extent;
            double y=(xyz[offset+1]- (double)bounds.center(1))/extent;
            double z=(xyz[offset+2]- (double)bounds.center(2))/extent;
            double horizontal=(x-y)*0.7071067811865476;
            double vertical=z*0.816496580927726-(x+y)*0.408248290463863;
            double forward=(x+y+z)*0.5773502691896258;
            double scale=size*0.61;
            return new double[]{size*0.5+horizontal*scale, size*0.5-vertical*scale, forward};
        }
        void pixel(int x,int y,double z,int color) throws IOException {
            if(++work>24_000_000L) throw new IOException("Thumbnail raster budget exceeded");
            if(x>=0&&y>=0&&x<size&&y<size) {
                int i=y*size+x;
                if(z>=depth[i]) { depth[i]=z; colors[i]=color; }
            }
        }
        Raster draw() throws IOException {
            check();
            if(doc.mesh!=null) {
                float[] xyz=doc.mesh.positions();
                for(int i=0;i<xyz.length;i+=9) { check(); triangle(project(xyz,i),project(xyz,i+3),project(xyz,i+6)); }
            } else {
                float[] xyz=doc.toolpath.positions(); boolean[] extruded=doc.toolpath.extrusion();
                for(int i=0;i<extruded.length;i++) { check(); line(project(xyz,i*6),project(xyz,i*6+3),extruded[i]?0xff56d8cf:0xff899cac); }
            }
            check(); return new Raster(size,size,colors);
        }
        static double cross(double ax,double ay,double bx,double by) { return ax*by-ay*bx; }
        void triangle(double[] a,double[] b,double[] c) throws IOException {
            double area=cross(b[0]-a[0],b[1]-a[1],c[0]-a[0],c[1]-a[1]);
            if(Math.abs(area)<1e-12) return;
            double ux=b[0]-a[0],uy=-(b[1]-a[1]),uz=(b[2]-a[2])*size*0.61;
            double vx=c[0]-a[0],vy=-(c[1]-a[1]),vz=(c[2]-a[2])*size*0.61;
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            double light=0.38+0.62*Math.abs((nx*-.3+ny*.5+nz*.81240384046)/len);
            int color=0xff000000|((int)(91*light)<<16)|((int)(210*light)<<8)|(int)(225*light);
            int left=Math.max(0,(int)Math.floor(Math.min(a[0],Math.min(b[0],c[0]))));
            int right=Math.min(size-1,(int)Math.ceil(Math.max(a[0],Math.max(b[0],c[0]))));
            int top=Math.max(0,(int)Math.floor(Math.min(a[1],Math.min(b[1],c[1]))));
            int bottom=Math.min(size-1,(int)Math.ceil(Math.max(a[1],Math.max(b[1],c[1]))));
            for(int y=top;y<=bottom;y++) { check(); for(int x=left;x<=right;x++) {
                // Count rejected samples too, so repeated large triangles cannot monopolize a worker.
                if(++work>24_000_000L) throw new IOException("Thumbnail raster budget exceeded");
                double u=cross(b[0]-x-.5,b[1]-y-.5,c[0]-x-.5,c[1]-y-.5)/area;
                double v=cross(c[0]-x-.5,c[1]-y-.5,a[0]-x-.5,a[1]-y-.5)/area;
                double w=1-u-v;
                if(u>=0&&v>=0&&w>=0) pixel(x,y,u*a[2]+v*b[2]+w*c[2],color);
            }}
        }
        void line(double[] a,double[] b,int color) throws IOException {
            int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]))));
            for(int i=0;i<=steps;i++) {
                double t=(double)i/steps;
                pixel((int)Math.round(a[0]+(b[0]-a[0])*t),(int)Math.round(a[1]+(b[1]-a[1])*t),a[2]+(b[2]-a[2])*t,color);
            }
        }
    }
}
