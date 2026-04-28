package org.example.aispingboot.controller;

import org.example.aispingboot.common.Result;
import org.example.aispingboot.exception.BusinessException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
public class FileUploadController {

    // 上传文件（封面图等），保存到本地 uploads 目录
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "businessType", required = false, defaultValue = "ARTICLE") String businessType,
            @RequestParam(value = "businessId", required = false) String businessId,
            @RequestParam(value = "businessField", required = false, defaultValue = "cover") String businessField) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("只能上传图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过5MB");
        }

        // 扩展名
        String originalName = file.getOriginalFilename();
        String ext = "png";
        if (originalName != null && originalName.contains(".")) {
            String candidate = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            if (candidate.matches("[a-z0-9]{1,5}")) {
                ext = candidate;
            }
        }
        // 文件名：优先用前端上传时生成的业务ID
        String filename = (businessId != null && !businessId.trim().isEmpty())
                ? businessId.trim() + "_" + businessField + "." + ext
                : UUID.randomUUID() + "." + ext;

        String dirName = businessType.toLowerCase();
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "uploads", dirName);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException("文件保存失败：" + e.getMessage());
        }

        String filePath = "/uploads/" + dirName + "/" + filename;
        Map<String, String> data = new HashMap<>();
        data.put("filePath", filePath);
        data.put("fileName", originalName);
        return Result.ok(data);
    }
}
