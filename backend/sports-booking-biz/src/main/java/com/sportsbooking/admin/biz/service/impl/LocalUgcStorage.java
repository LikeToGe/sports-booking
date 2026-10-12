package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.po.UgcUpload;
import com.sportsbooking.admin.biz.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** UGC 私有本地目录；公开读取由内容版本与审核状态控制。 */
@Service
public class LocalUgcStorage {
    private static final long IMAGE_LIMIT = 5L * 1024 * 1024;
    private static final long VIDEO_LIMIT = 30L * 1024 * 1024;
    private final Path directory;

    public LocalUgcStorage(@Value("${app.upload.directory}") String uploadDirectory) {
        this.directory = Path.of(uploadDirectory).toAbsolutePath().normalize().resolveSibling("ugc-media");
    }

    /** 从实际字节识别媒体，客户端 MIME 和时长只作交叉核对。 */
    public UgcUpload store(MultipartFile file, Integer reportedDurationSeconds) {
        if (file == null || file.isEmpty() || file.getSize() > VIDEO_LIMIT) {
            throw invalid();
        }
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "incoming-", ".tmp");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(temporary);
            if (size == 0 || size > VIDEO_LIMIT) {
                throw invalid();
            }
            String mime = detectMime(temporary);
            boolean video = "video/mp4".equals(mime);
            if (!video && size > IMAGE_LIMIT) {
                throw invalid();
            }
            Integer duration = video ? readVideoDuration(temporary) : null;
            if (video && (reportedDurationSeconds == null || Math.abs(reportedDurationSeconds - duration) > 1)) {
                throw invalid();
            }
            String extension = switch (mime) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                default -> ".mp4";
            };
            String key = UUID.randomUUID() + extension;
            String digest = sha256(temporary);
            Files.move(temporary, directory.resolve(key));
            temporary = null;
            UgcUpload upload = new UgcUpload();
            upload.mediaType = video ? "VIDEO" : "IMAGE";
            upload.objectKey = key;
            upload.mimeType = mime;
            upload.sizeBytes = size;
            upload.sha256 = digest;
            upload.durationSeconds = duration;
            return upload;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UGC_UPLOAD_FAILED);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    public Path file(String objectKey) {
        Path path = directory.resolve(objectKey).normalize();
        if (!path.getParent().equals(directory)) {
            throw invalid();
        }
        return path;
    }

    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(file(objectKey));
    }

    private String detectMime(Path path) throws IOException {
        byte[] head;
        try (InputStream input = Files.newInputStream(path)) {
            head = input.readNBytes(16);
        }
        if (head.length < 12) throw invalid();
        if ((head[0] & 255) == 0xff && (head[1] & 255) == 0xd8 && (head[2] & 255) == 0xff) {
            requireImageDimensions(path);
            return "image/jpeg";
        }
        if ((head[0] & 255) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 13 && head[5] == 10 && head[6] == 26 && head[7] == 10) {
            requireImageDimensions(path);
            return "image/png";
        }
        if (new String(head, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")) {
            return "video/mp4";
        }
        throw invalid();
    }

    private void requireImageDimensions(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 12000 || height > 12000 || width * height > 40_000_000) {
                    throw invalid();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private int readVideoDuration(Path path) throws IOException {
        try {
            MovieBox movie = MP4Util.parseMovie(path.toFile());
            if (movie == null || movie.getVideoTrack() == null || movie.getTimescale() <= 0
                    || movie.getDuration() <= 0) {
                throw invalid();
            }
            long seconds = (movie.getDuration() + movie.getTimescale() - 1L) / movie.getTimescale();
            if (seconds < 1 || seconds > 20) throw invalid();
            return (int) seconds;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }
}
