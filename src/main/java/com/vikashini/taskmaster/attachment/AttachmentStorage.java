package com.vikashini.taskmaster.attachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Keeps attachment bytes in a folder on disk. Files are named by a random key, never by
 * anything the uploader sent, so a crafted file name cannot escape the folder or
 * overwrite another file.
 *
 * <p>The database and the disk cannot share a transaction, so the two are kept in step
 * by hand: a file saved during a transaction that rolls back is deleted again, and files
 * of deleted attachments are removed only after the deletion commits.
 */
@Component
public class AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);

    private final Path directory;

    AttachmentStorage(Properties properties) {
        this.directory = properties.directory().toAbsolutePath().normalize();
    }

    /** Saves the upload and returns the key it was stored under. */
    public String store(MultipartFile file) {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(directory);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store attachment", e);
        }
        whenRolledBack(() -> deleteQuietly(key));
        return key;
    }

    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isReadable(path)) {
            throw new UncheckedIOException(new IOException("Attachment file is missing: " + key));
        }
        return new FileSystemResource(path);
    }

    /** Removes the files once the current transaction commits (or now, if there is none). */
    public void deleteAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<String> copy = List.copyOf(keys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    copy.forEach(AttachmentStorage.this::deleteQuietly);
                }
            });
        } else {
            copy.forEach(this::deleteQuietly);
        }
    }

    private void whenRolledBack(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
        }
    }

    private Path resolve(String key) {
        Path path = directory.resolve(key).normalize();
        // Keys are generated here, so this only guards against a corrupted database row.
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | RuntimeException e) {
            // An orphaned file wastes space but breaks nothing; do not fail the request.
            log.warn("Could not delete attachment file {}", key, e);
        }
    }

    /** {@code app.attachments.*} */
    @ConfigurationProperties("app.attachments")
    record Properties(Path directory) {
    }
}
