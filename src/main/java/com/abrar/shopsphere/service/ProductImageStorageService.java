
package com.abrar.shopsphere.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductImageStorageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png");

    private final Path root;

    public ProductImageStorageService(
            @Value("${shopsphere.upload-dir:./uploads}") String directory) {

        this.root = Paths.get(directory)
                .toAbsolutePath()
                .normalize();
    }

    public String store(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an image file.");
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Image must be 5 MB or smaller."
            );
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);

        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Only JPEG and PNG images are accepted."
            );
        }

        try {
            BufferedImage decoded;

            try (InputStream input = file.getInputStream()) {
                decoded = ImageIO.read(input);
            }

            if (decoded == null
                    || decoded.getWidth() <= 0
                    || decoded.getHeight() <= 0
                    || decoded.getWidth() > 10000
                    || decoded.getHeight() > 10000) {
                throw new IllegalArgumentException(
                        "The uploaded file is not a supported image."
                );
            }

            Files.createDirectories(root);

            String extension = contentType.equals("image/png")
                    ? ".png"
                    : ".jpg";

            String filename = UUID.randomUUID() + extension;

            Path target = root.resolve(filename).normalize();

            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("Invalid image path.");
            }

            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return "/uploads/" + filename;

        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not store product image.",
                    exception
            );
        }
    }

    public void deleteByPublicUrl(String publicUrl) {

        if (publicUrl == null || !publicUrl.startsWith("/uploads/")) {
            return;
        }

        String filename = publicUrl.substring("/uploads/".length());

        if (filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || filename.equals(".")
                || filename.equals("..")) {
            return;
        }

        try {
            Path target = root.resolve(filename).normalize();

            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (Exception exception) {
            // File cleanup must not hide the original business operation.
        }
    }
}
