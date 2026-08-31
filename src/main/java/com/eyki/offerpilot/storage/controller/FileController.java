package com.eyki.offerpilot.storage.controller;

import com.eyki.offerpilot.storage.service.FileStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * File proxy controller.
 * Serves files from MinIO through the backend to avoid exposing MinIO directly.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * Download/serve a file.
     * Supports both inline viewing and attachment download via the 'download' query param.
     */
    @GetMapping("/{*filePath}")
    public StreamingResponseBody getFile(@PathVariable String filePath,
                                         @RequestParam(value = "download", required = false) boolean forceDownload,
                                         HttpServletResponse response) {
        // Decode URL-encoded path
        String decodedPath = java.net.URLDecoder.decode(filePath, StandardCharsets.UTF_8);

        if (!fileStorageService.fileExists(decodedPath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return outputStream -> outputStream.write("文件不存在".getBytes(StandardCharsets.UTF_8));
        }

        // Determine content type
        String contentType = determineContentType(decodedPath);
        response.setContentType(contentType);

        if (forceDownload) {
            String encodedFileName = URLEncoder.encode(decodedPath, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        } else {
            // For PDFs and images, display inline; for others, download
            if (contentType.startsWith("image/") || "application/pdf".equals(contentType)) {
                response.setHeader("Content-Disposition", "inline");
            } else {
                String encodedFileName = URLEncoder.encode(decodedPath, StandardCharsets.UTF_8).replace("+", "%20");
                response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            }
        }

        InputStream inputStream = fileStorageService.download(decodedPath);
        return outputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
        };
    }

    private String determineContentType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xls")) return "application/vnd.ms-excel";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".json")) return MediaType.APPLICATION_JSON_VALUE;
        if (name.endsWith(".txt")) return MediaType.TEXT_PLAIN_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}