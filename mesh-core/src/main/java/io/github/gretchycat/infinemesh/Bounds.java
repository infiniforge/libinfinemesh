package io.github.gretchycat.infinemesh;

public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public static Bounds of(float[] xyz) {
        if (xyz.length == 0 || xyz.length % 3 != 0) throw new IllegalArgumentException("Empty or invalid geometry");
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < xyz.length; i++) {
            if (!Float.isFinite(xyz[i])) throw new IllegalArgumentException("Non-finite coordinate");
            int a = i % 3; min[a] = Math.min(min[a], xyz[i]); max[a] = Math.max(max[a], xyz[i]);
        }
        return new Bounds(min[0], min[1], min[2], max[0], max[1], max[2]);
    }
    public float center(int axis) {
        return (float) switch (axis) {
            case 0 -> ((double) minX + maxX) / 2;
            case 1 -> ((double) minY + maxY) / 2;
            default -> ((double) minZ + maxZ) / 2;
        };
    }
    public double extent() {
        return Math.max((double) maxX - minX, Math.max((double) maxY - minY, (double) maxZ - minZ));
    }
}
