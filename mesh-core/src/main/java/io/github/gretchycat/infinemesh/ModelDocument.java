package io.github.gretchycat.infinemesh;
import java.util.*;

public final class ModelDocument {
    public final String format;
    public final MeshGeometry mesh;
    public final Toolpath toolpath;
    public final List<String> warnings;
    public ModelDocument(String format, MeshGeometry mesh, Toolpath toolpath, List<String> warnings) {
        if ((mesh == null) == (toolpath == null)) throw new IllegalArgumentException("One geometry type required");
        this.format=format; this.mesh=mesh; this.toolpath=toolpath; this.warnings=Collections.unmodifiableList(new ArrayList<>(warnings));
    }
    public Bounds bounds() { return mesh!=null ? mesh.bounds() : toolpath.bounds(); }
}
