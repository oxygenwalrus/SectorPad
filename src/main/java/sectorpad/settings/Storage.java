package sectorpad.settings;

import java.io.IOException;
import java.util.List;

/** Small document storage boundary; runtime implementations use only supported game APIs. */
public interface Storage {
    interface Lease extends AutoCloseable { @Override void close() throws IOException; }
    boolean exists(String name) throws IOException;
    String read(String name) throws IOException;
    default String readRaw(String name) throws IOException { return read(name); }
    void write(String name, String text) throws IOException;
    List<String> list(String directory) throws IOException;
    String readExternal(String location) throws IOException;
    String location();
    boolean supportsAtomicReplacement();
    default String warning() { return ""; }
    Lease acquire() throws IOException;
}
