package io.github.gretchycat.infinemesh;

import java.util.*;

/** Shared format identities for file associations, capability discovery and thumbnails. */
public final class ModelFormats {
    private ModelFormats() {}
    public static final Set<String> STL_MIMES=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("model/stl","application/sla","application/vnd.ms-pki.stl","application/x-stl")));
    public static final Set<String> GCODE_MIMES=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("text/x-gcode","application/x-gcode","text/x.gcode","application/gcode")));
    public static final Set<String> GCODE_EXTENSIONS=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("gcode","gco","gc","g","ngc")));
    public static String mimeType(String name,String mime) {
        String ext=name==null?"":name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(ext.equals("stl")) return "model/stl";
        if(GCODE_EXTENSIONS.contains(ext)) return "text/x-gcode";
        String normalized=mime==null?"":mime.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
        if(STL_MIMES.contains(normalized)) return "model/stl";
        if(GCODE_MIMES.contains(normalized)) return "text/x-gcode";
        return null;
    }
    public static boolean supports(String name,String mime) { return mimeType(name,mime)!=null; }
}
