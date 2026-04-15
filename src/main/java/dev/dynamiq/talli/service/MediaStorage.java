package dev.dynamiq.talli.service;

import java.io.InputStream;

/**
 * Contract for storing and retrieving file bytes. Implementations decide
 * where bytes actually live (local disk, S3, etc.). MediaService depends on
 * this interface, not on any specific backend.
 */
public interface MediaStorage {

    /** Store the given bytes at the given key. Overwrites if the key already exists. */
    void store(String key, InputStream bytes, long size);

    /** Open a stream for reading the bytes at the given key. Caller must close the stream. */
    InputStream read(String key);

    /** Delete the bytes at the given key. No-op if the key does not exist. */
    void delete(String key);

    /** True if bytes exist at the given key. */
    boolean exists(String key);
}
