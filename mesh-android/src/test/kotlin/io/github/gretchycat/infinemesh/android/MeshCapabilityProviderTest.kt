package io.github.gretchycat.infinemesh.android
import io.github.gretchycat.infinemesh.ModelFormats
import io.github.gretchycat.infinistream.*
import org.junit.Assert.*
import org.junit.Test

class MeshCapabilityProviderTest {
    @Test fun discoveryDeclaresCanonicalAssociationsAndExecutableThumbnailService() {
        val handler = MeshCapabilityProvider().handlers().single()
        val descriptor = handler.describe()
        assertEquals("true", descriptor.attributes["encodedImageExtractor"])
        assertNotNull(handler.service(EncodedImageExtractor::class.java))
        for (ext in ModelFormats.GCODE_EXTENSIONS + "stl") {
            val matches = handler.detectExtensions("part.v2.${ext.uppercase()}")
            assertEquals(1, matches.size)
            assertEquals(setOf(if(ext == "stl") "model/stl" else "text/x-gcode"), matches.single().format.mimeTypes)
        }
    }
    @Test fun metadataHintsAreCandidatesWithoutPretendingToValidateBytes() {
        val handler = MeshThumbnailHandler()
        val context = CapabilityProbeContext(CapabilityResource(StreamMetadata(ContentType(mimeType="text/plain"), name="part.gcode")))
        assertEquals(MatchConfidence.CANDIDATE, handler.probe(context).confidence)
        assertTrue(handler.validateMagicBytes("solid unrelated text".toByteArray()).isEmpty())
        val unknown = CapabilityProbeContext(CapabilityResource(StreamMetadata(name="notes.txt")))
        assertEquals(MatchConfidence.NONE, handler.probe(unknown).confidence)
    }
}
