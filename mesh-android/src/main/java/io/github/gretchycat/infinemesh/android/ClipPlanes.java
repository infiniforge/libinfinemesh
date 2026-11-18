package io.github.gretchycat.infinemesh.android;

/** Fit depth precision to the normalized scene instead of reserving mostly empty space. */
final class ClipPlanes {
    final float near,far;
    private ClipPlanes(float near,float far) { this.near=near;this.far=far; }
    static ClipPlanes fit(float distance,float radius) {
        float padding=Math.max(0.05f,radius*0.05f);
        float minimumNear=Math.max(0.001f,radius*0.005f);
        return new ClipPlanes(Math.max(minimumNear,distance-radius-padding),
            Math.max(minimumNear*2,distance+radius+padding));
    }
}
