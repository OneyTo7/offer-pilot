package com.eyki.offerpilot.storage.service;

import java.io.InputStream;

/**
 * File storage service interface. Abstracts the underlying object storage (MinIO).
 */
public interface FileStorageService {

    /**
     * Upload a file.
     *
     * @param fileName    the file name (path) in storage
     * @param inputStream file content
     * @param contentType MIME type
     * @return the accessible URL of the file
     */
    String upload(String fileName, InputStream inputStream, String contentType);

    /**
     * Download a file.
     *
     * @param fileName the file name (path) in storage
     * @return input stream of the file content
     */
    InputStream download(String fileName);

    /**
     * Delete a file.
     *
     * @param fileName the file name (path) in storage
     */
    void delete(String fileName);

    /**
     * Get the public URL of a file.
     *
     * @param fileName the file name (path) in storage
     * @return the accessible URL
     */
    String getFileUrl(String fileName);

    /**
     * Check if a file exists.
     *
     * @param fileName the file name (path) in storage
     * @return true if the file exists
     */
    boolean fileExists(String fileName);
}