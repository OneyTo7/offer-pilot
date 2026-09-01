package com.eyki.offerpilot.storage.service.impl;

import com.eyki.offerpilot.storage.config.MinioConfig;
import com.eyki.offerpilot.storage.service.FileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MinIO object storage implementation. Provides file upload, download, delete, existence check,
 * and URL generation. Auto-creates the configured bucket on startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorage implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            boolean bucketExists =
                minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("MinIO bucket 已创建: {}", minioConfig.getBucket());
            } else {
                log.info("MinIO bucket 已存在: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败（MinIO 可能未启动）: {}", e.getMessage());
        }
    }

    @Override
    public String upload(String fileName, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName)
                .stream(inputStream, -1, 10 * 1024 * 1024) // max 10MB per part
                .contentType(contentType).build());
            log.info("文件上传成功: bucket={}, fileName={}", minioConfig.getBucket(), fileName);
            return getFileUrl(fileName);
        } catch (Exception e) {
            log.error("文件上传失败: fileName={}", fileName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String fileName) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName).build());
        } catch (Exception e) {
            log.error("文件下载失败: fileName={}", fileName, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName).build());
            log.info("文件删除成功: fileName={}", fileName);
        } catch (Exception e) {
            log.error("文件删除失败: fileName={}", fileName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFileUrl(String fileName) {
        // For local/dev: return the direct MinIO URL
        // In production, this could return a signed URL or proxy URL
        return String.format("%s/%s/%s", minioConfig.getEndpoint(), minioConfig.getBucket(), fileName);
    }

    @Override
    public boolean fileExists(String fileName) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileName).build());
            return true;
        } catch (MinioException e) {
            return false;
        } catch (Exception e) {
            log.warn("检查文件存在失败: fileName={}", fileName, e);
            return false;
        }
    }
}