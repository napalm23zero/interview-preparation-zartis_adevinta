package dev.hustletech.loopmarket.app;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class DemoPhotoSeederTest {
    @Test void copiesValidIllustrationsWithoutReplacingAnExistingFile() throws Exception {
        Path storage = Files.createTempDirectory("loopmarket-demo-photos-");
        DemoPhotoSeeder.seed(storage);
        for (int index = 701; index <= 706; index++) {
            Path image = storage.resolve("01900000-0000-7000-8000-%012d".formatted(index));
            assertNotNull(ImageIO.read(image.toFile()));
        }
        Path first = storage.resolve("01900000-0000-7000-8000-000000000701");
        Files.writeString(first, "existing");
        DemoPhotoSeeder.seed(storage);
        assertEquals("existing", Files.readString(first));
    }
}
