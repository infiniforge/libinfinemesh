package io.github.gretchycat.infinemesh.android

import android.graphics.Bitmap
import io.github.gretchycat.infinemesh.*
import io.github.gretchycat.infinistream.*
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream

/** Cheap, context-free discovery; Android bitmap APIs run only on an explicit thumbnail request. */
class MeshCapabilityProvider : CapabilityProvider {
    override val library = "libinfinemesh"
    override fun handlers(): List<CapabilityHandler> = listOf(MeshThumbnailHandler())
}

class MeshThumbnailHandler : EncodedImageExtractor {
    override val descriptor = HandlerDescriptor(
        id = "libinfinemesh.model", library = "libinfinemesh", title = "STL and G-code model preview",
        formats = listOf(
            FormatSupport(mimeTypes = setOf("model/stl"), extensions = setOf("stl")),
            FormatSupport(mimeTypes = setOf("text/x-gcode"), extensions = ModelFormats.GCODE_EXTENSIONS),
            FormatSupport(mimeTypes = ModelFormats.STL_MIMES - "model/stl"),
            FormatSupport(mimeTypes = ModelFormats.GCODE_MIMES - "text/x-gcode")
        ),
        operations = setOf(Operations.DECODE, Operations.VIEW, Operations.THUMBNAIL),
        limitations = listOf("Read-only preview; unknown commands and invalid G-code text are skipped.",
            "Thumbnails: 16 MiB input, 100000 triangles / 200000 segments, 512 pixels, bounded raster work."),
        priority = 80,
        attributes = mapOf("viewerTarget" to "external", "viewerPackage" to "com.infinimesh", "presentation" to "model")
    )
    override fun extract(source: ByteSource, targetPixels: Int, cancellation: CancellationToken): ExtractedImage? {
        cancellation.check()
        val metadata = source.metadata
        if (!ModelFormats.supports(metadata.name, metadata.content.mimeType)) return null
        val input = object : FilterInputStream(source.asInputStream(ownsSource = false)) {
            override fun read(): Int { cancellation.check(); return super.read() }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                cancellation.check(); return `in`.read(bytes, offset, length)
            }
        }
        val model = ModelLoader().load(input, metadata.name, metadata.content.mimeType,
            DecodeLimits(16 * 1024 * 1024, 100_000, 200_000))
        val raster = ModelThumbnail.render(model, targetPixels.coerceIn(1,512)) { cancellation.check() }
        cancellation.check()
        val bitmap = Bitmap.createBitmap(raster.argb(), raster.width(), raster.height(), Bitmap.Config.ARGB_8888)
        try {
            val bytes = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) { "PNG thumbnail encoding failed" }
            cancellation.check()
            return ExtractedImage.ownedBytes(bytes.toByteArray(), "image/png", metadata)
        } finally { bitmap.recycle() }
    }
}
