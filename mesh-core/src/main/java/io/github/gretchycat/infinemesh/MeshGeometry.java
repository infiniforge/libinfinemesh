package io.github.gretchycat.infinemesh;

import java.util.HashMap;
import java.util.Map;

/** Triangle soup in source coordinates. No topology edits or writers are provided. */
public final class MeshGeometry {
    private final float[] positions;
    private final Bounds bounds;
    public MeshGeometry(float[] positions) {
        if (positions.length == 0 || positions.length % 9 != 0) throw new IllegalArgumentException("Expected triangles");
        this.positions = positions.clone(); bounds = Bounds.of(this.positions);
    }
    public int triangleCount() { return positions.length / 9; }
    public float[] positions() { return positions.clone(); }
    public Bounds bounds() { return bounds; }
    private record Vertex(float x, float y, float z) {
        static Vertex of(float[] p, int i) { return new Vertex(p[i] == 0 ? 0 : p[i], p[i+1] == 0 ? 0 : p[i+1], p[i+2] == 0 ? 0 : p[i+2]); }
    }
    /** Geometry-derived normals; Gouraud mode area-weights faces sharing exact positions. */
    public float[] normals(boolean smooth) {
        float[] result = new float[positions.length];
        Map<Vertex, double[]> sums = smooth ? new HashMap<>() : null;
        for (int i = 0; i < positions.length; i += 9) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Normal preparation cancelled");
            double ax = (double)positions[i+3]-positions[i], ay = (double)positions[i+4]-positions[i+1], az = (double)positions[i+5]-positions[i+2];
            double bx = (double)positions[i+6]-positions[i], by = (double)positions[i+7]-positions[i+1], bz = (double)positions[i+8]-positions[i+2];
            double[] n = {ay*bz-az*by, az*bx-ax*bz, ax*by-ay*bx};
            for (int v = i; v < i+9; v += 3) {
                if (smooth) {
                    double[] sum = sums.computeIfAbsent(Vertex.of(positions,v), k -> new double[3]);
                    for (int a=0; a<3; a++) sum[a] += n[a];
                } else storeNormal(result, v, n);
            }
        }
        if (smooth) for (int v=0; v<positions.length; v+=3) storeNormal(result,v,sums.get(Vertex.of(positions,v)));
        return result;
    }
    private static void storeNormal(float[] out, int v, double[] n) {
        double len = Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
        for (int a=0; a<3; a++) out[v+a] = len > 0 ? (float)(n[a]/len) : (a==2 ? 1 : 0);
    }
}
