# Public API, ownership and supported subsets

## Core

Package: `io.github.gretchycat.infinemesh`.

`ModelLoader()` selects the built-in `StlDecoder` and `GcodeDecoder`. Call
`load(InputStream, displayName, mime, DecodeLimits)` from a worker. The caller owns
and closes the stream. Inputs are bounded before decode; invalid/unsupported data
throws IOException. Registered decoders use filename, MIME or a bounded prefix;
STL recognizes textual `solid` prefixes, while binary STL needs an STL name/MIME.
Supply a custom ordered `List<ModelDecoder>` for explicit extension. Each decoder
receives bounded encoded bytes and the caller's resource budgets.

`ModelDocument` has exactly one immutable `MeshGeometry` or `Toolpath`, a format
label and immutable warnings. Geometry constructors copy input arrays; array
accessors return copies. `Bounds` exposes source-coordinate extents.
`MeshGeometry.normals(false)` returns geometry-derived face normals repeated per
vertex; `normals(true)` returns normalized area-weighted sums at coincident float
positions (signed zero is unified). No crease-angle or manifold requirement.

`Toolpath` exposes line-pair XYZ positions in millimeters, an extrusion flag and
cumulative end time per segment. `completedSegments(seconds)` uses binary search;
`headAt(seconds)` interpolates the current segment, including stationary dwell and
extrusion-only segments. Preview timing assumes constant feed and documented
initial machine state. There are no writers or transport/controller APIs.

Default limits: 64 MiB input, 500,000 triangles, 1,000,000 segments. Callers can
supply `DecodeLimits`; limits do not guarantee sufficient heap. Loading observes
thread interruption. STL validates exact binary lengths, finite vertices and
complete ASCII structures, ignores binary color attributes and regenerates normals.

G-code subset: G0/G1; XY I/J G2/G3, including full circles and helices; G4 P/S;
G17; G20/G21; G90/G91; M82/M83; G92 offsets; approximate zero-origin G28; T0.
G90/G91 clear M82/M83 override as in Marlin. Retraction, dwell and extrusion-only
moves retain preview time. Default feed is 1200 mm/min. G0 uses the current feed.
Machine M18/84/104/109/140/190/106/107/105/114/115/201/203/204/205/400/900 are
accepted but not simulated and emit a warning; M117/118 display messages are
ignored. Unknown commands, radius arcs, non-XY planes and multiple tools fail.
All previews warn about missing acceleration, machine state and thermal timing.
Checksums are ignored; semicolon/parenthesized comments and line numbers work.

## Android

Package: `io.github.gretchycat.infinemesh.android`.

Create `MeshView(Context)`, attach it to a layout, and forward host `onResume()` and
`onPause()`. Invoke `setDocument(ModelDocument)` from a worker; normal and direct
buffer preparation happens synchronously on that caller, then queues the scene to
the GL thread. New documents reset camera and show the full toolpath. Set playback
time after the load to override that default. UI setters may be called on the main
thread: `setShading(Shading.WIREFRAME/FLAT/GOURAUD)`, `setColors(ARGB, ARGB)`,
`setGridVisible`, `setTravelVisible`, `setPreviewSeconds`, `resetRotation`,
`resetZoom`, `zoomBy`. Host owns its preview clock and settings. Drag and pinch are
built in; +/- or other accessible zoom controls can call `zoomBy`.

Gouraud uses vertex lighting with interpolated intensity; flat mode repeats the
same face normal at all three vertices. Wireframe includes triangle diagonals.
G-code is line geometry in every shading mode. Hidden travel still consumes time.
The ground grid follows source bounds and is not a physical measurement scale.
CPU arrays survive GL context recreation. Color setters apply to all untextured
surface/printed path geometry. Travel is gray and the head amber.

## Extension and compatibility

Initial API version: 0.1.0. Only triangle soup and toolpaths are represented.
Indexed meshes, scenes, texture resources, UVs and materials need future public
API extensions; do not treat the decoder registry as existing texture support.
No application identity, file picker or private app data belongs to this library.
Maven publishing is future work; current consumers use pinned source composites.
