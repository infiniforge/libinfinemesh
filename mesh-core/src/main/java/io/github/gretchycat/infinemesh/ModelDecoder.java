package io.github.gretchycat.infinemesh;
import java.io.IOException;

/** Extension point for future formats, with no application dependency. */
public interface ModelDecoder {
    boolean supports(String name, String mime, byte[] prefix);
    ModelDocument decode(byte[] bytes, DecodeLimits limits) throws IOException;
}
