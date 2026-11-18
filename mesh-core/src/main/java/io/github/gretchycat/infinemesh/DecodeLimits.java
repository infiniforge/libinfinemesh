package io.github.gretchycat.infinemesh;

/** Per-load resource budgets; callers may provide larger limits for trusted files. */
public record DecodeLimits(int maxBytes, int maxTriangles, int maxSegments) {
    public static final DecodeLimits DEFAULT = new DecodeLimits(64 * 1024 * 1024, 500_000, 1_000_000);
    public DecodeLimits {
        if (maxBytes < 84 || maxTriangles < 1 || maxSegments < 1) throw new IllegalArgumentException("Invalid decode limits");
    }
}
