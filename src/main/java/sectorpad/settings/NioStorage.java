package sectorpad.settings;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/** Headless/tooling backend only. Never instantiate this from Starsector's restricted script loader. */
public final class NioStorage implements Storage {
    private final Path root;
    private boolean atomic = true;
    public NioStorage(Path root) { this.root = root.toAbsolutePath().normalize(); }
    private Path path(String name) throws IOException {
        Path result = root.resolve(name).normalize(); if (!result.startsWith(root)) throw new IOException("Document path escaped settings directory."); return result;
    }
    public boolean exists(String name) throws IOException { return Files.exists(path(name)); }
    public String read(String name) throws IOException { return readPath(path(name)); }
    public String readExternal(String location) throws IOException { return readPath(Path.of(location).toAbsolutePath().normalize()); }
    private static String readPath(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > ProfileStore.MAX_FILE_BYTES) throw new IOException("Invalid or oversized configuration file.");
        try (var stream = Files.newInputStream(file)) {
            byte[] bytes = stream.readNBytes((int) ProfileStore.MAX_FILE_BYTES + 1);
            if (bytes.length > ProfileStore.MAX_FILE_BYTES) throw new IOException("Configuration exceeds the size limit.");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    public void write(String name, String text) throws IOException {
        Path target = path(name); Files.createDirectories(target.getParent());
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); if (bytes.length > ProfileStore.MAX_FILE_BYTES) throw new IOException("Configuration exceeds the size limit.");
        Path temp = target.resolveSibling("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { atomic = false; Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            try (FileChannel directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { directory.force(true); }
            catch (IOException | UnsupportedOperationException unsupported) { }
        } finally { Files.deleteIfExists(temp); }
    }
    public List<String> list(String directory) throws IOException {
        Path parent = path(directory); if (!Files.isDirectory(parent)) return List.of();
        try (var files = Files.list(parent)) { return files.filter(file -> !Files.isSymbolicLink(file) && Files.isRegularFile(file) && file.getFileName().toString().matches("[a-z0-9][a-z0-9_-]{0,47}\\.json"))
                .sorted().limit(ProfileStore.MAX_PROFILES).map(file -> root.relativize(file).toString().replace('\\', '/')).toList(); }
    }
    public String location() { return root.toString(); }
    public boolean supportsAtomicReplacement() { return atomic; }
    public Lease acquire() throws IOException {
        Files.createDirectories(root); FileChannel channel = FileChannel.open(root.resolve("settings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock(); if (lock == null) throw new IOException("Another SectorPad instance is writing settings. Try Save again.");
            return () -> { try { lock.close(); } finally { channel.close(); } };
        } catch (OverlappingFileLockException busy) { channel.close(); throw new IOException("Another SectorPad instance is writing settings. Try Save again.", busy); }
        catch (IOException failure) { channel.close(); throw failure; }
    }
}
