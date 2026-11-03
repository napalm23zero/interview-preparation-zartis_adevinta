package dev.hustletech.loopmarket.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;

// Copies bundled demo illustrations once; existing files keep their place in the galaxy.
final class DemoPhotoSeeder {
    private DemoPhotoSeeder() {}
    static void seed(Path storage) throws IOException {
        Files.createDirectories(storage);
        for (int index = 501; index <= 506; index++) {
            String key = "01900000-0000-7000-8000-%012d".formatted(index);
            try (var input = DemoPhotoSeeder.class.getResourceAsStream("/db/demo/photos/" + key + ".png")) {
                if (input == null) throw new IOException("Missing demo illustration: " + key);
                try { Files.copy(input, storage.resolve(key)); }
                catch (FileAlreadyExistsException ignored) { /* The existing file rules this sector. */ }
            }
        }
    }
}
