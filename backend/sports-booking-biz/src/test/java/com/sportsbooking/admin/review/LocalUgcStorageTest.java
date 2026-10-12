package com.sportsbooking.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.service.impl.LocalUgcStorage;
import java.nio.file.Path;
import java.util.Base64;
import java.nio.file.Files;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.ColorSpace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** 媒体格式以实际文件内容判定。 */
class LocalUgcStorageTest {
    @TempDir Path directory;

    @Test
    void storesActualPngAndRejectsSpoofedOrOversizedFiles() {
        LocalUgcStorage storage = new LocalUgcStorage(directory.resolve("venue-uploads").toString());
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pZ8AAAAASUVORK5CYII=");
        var upload = storage.store(new MockMultipartFile("file", "test.txt", "text/plain", png), null);

        assertThat(upload.mimeType).isEqualTo("image/png");
        assertThat(upload.sha256).hasSize(64);
        assertThat(storage.file(upload.objectKey)).exists();
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()), null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "huge.png", "image/png", new byte[30 * 1024 * 1024 + 1]), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void videoDurationComesFromActualMp4AndOversizedDurationIsRejected() throws Exception {
        Path fixtures = Path.of("target/p4-fixtures");
        Files.createDirectories(fixtures);
        Path shortVideo = fixtures.resolve("two-seconds.mp4");
        encodeVideo(shortVideo,2);
        LocalUgcStorage storage = new LocalUgcStorage(directory.resolve("venue-uploads").toString());
        MockMultipartFile file = new MockMultipartFile("file","video.mp4","video/mp4",Files.readAllBytes(shortVideo));
        var upload = storage.store(file,2);
        assertThat(upload.durationSeconds).isEqualTo(2);
        assertThat(upload.mimeType).isEqualTo("video/mp4");
        assertThatThrownBy(() -> storage.store(file,9)).isInstanceOf(ApiException.class);
        Path longVideo = directory.resolve("too-long.mp4");
        encodeVideo(longVideo,21);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file","video.mp4","video/mp4",
                Files.readAllBytes(longVideo)),2)).isInstanceOf(ApiException.class);
    }

    private void encodeVideo(Path path, int seconds) throws Exception {
        SequenceEncoder encoder = SequenceEncoder.createSequenceEncoder(path.toFile(),1);
        Picture frame = Picture.create(16,16,ColorSpace.RGB);
        for (int index = 0; index < seconds; index++) encoder.encodeNativeFrame(frame);
        encoder.finish();
    }
}
