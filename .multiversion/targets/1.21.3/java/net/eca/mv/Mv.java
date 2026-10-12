// OVERLAY-ONLY: community fork multi-version layer (not the authors' code). Helpers that stand in for
// vanilla methods removed in 1.21.2, so the authors' call sites need only a rename row.
package net.eca.mv;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Mv {
    private Mv() {
    }

    /** 1.21.1's NativeImage.asByteArray(): the image encoded as PNG. 1.21.2 only writes PNGs to files. */
    public static byte[] pngBytes(NativeImage image) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("eca-preview", ".png");
            image.writeToFile(tmp);
            return Files.readAllBytes(tmp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
