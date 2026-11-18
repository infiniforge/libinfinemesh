package io.github.gretchycat.infinemesh;

/** Timed, read-only preview segments in millimeters. */
public final class Toolpath {
    private final float[] positions;
    private final boolean[] extrusion;
    private final double[] endSeconds;
    private final Bounds bounds;
    public Toolpath(float[] positions, boolean[] extrusion, double[] endSeconds) {
        if (positions.length == 0 || positions.length != extrusion.length*6 || extrusion.length != endSeconds.length)
            throw new IllegalArgumentException("Invalid toolpath arrays");
        double last=0;
        for (double t:endSeconds) { if (!Double.isFinite(t) || t<last) throw new IllegalArgumentException("Invalid timing"); last=t; }
        this.positions=positions.clone(); this.extrusion=extrusion.clone(); this.endSeconds=endSeconds.clone(); bounds=Bounds.of(this.positions);
    }
    public float[] positions() { return positions.clone(); }
    public boolean[] extrusion() { return extrusion.clone(); }
    public double[] endSeconds() { return endSeconds.clone(); }
    public Bounds bounds() { return bounds; }
    public int segmentCount() { return extrusion.length; }
    public double durationSeconds() { return endSeconds[endSeconds.length-1]; }
    /** Number of completely drawn segments at the specified preview time. */
    public int completedSegments(double seconds) {
        int lo=0, hi=endSeconds.length;
        while(lo<hi) { int mid=(lo+hi)>>>1; if(endSeconds[mid]<=seconds) lo=mid+1; else hi=mid; }
        return lo;
    }
    public float[] headAt(double seconds) {
        int i=completedSegments(seconds);
        if(i==segmentCount()) return new float[]{positions[positions.length-3],positions[positions.length-2],positions[positions.length-1]};
        double start=i==0 ? 0 : endSeconds[i-1], duration=endSeconds[i]-start;
        double f=duration<=0 ? 1 : Math.max(0,Math.min(1,(seconds-start)/duration));
        return new float[]{(float)(positions[i*6]+((double)positions[i*6+3]-positions[i*6])*f),
            (float)(positions[i*6+1]+((double)positions[i*6+4]-positions[i*6+1])*f),
            (float)(positions[i*6+2]+((double)positions[i*6+5]-positions[i*6+2])*f)};
    }
}
