package io.communet.fileser.web.controller;

import com.google.common.base.Throwables;
import io.communet.fileser.common.exception.ServiceException;
import io.communet.fileser.common.vo.Response;
import io.communet.fileser.utils.UnicodeUtil;
import io.communet.fileser.web.configuration.WebConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * <p>function:
 * <p>User: LeeJohn
 * <p>Date: 2019/06/17
 * <p>Version: 1.0
 */
@Slf4j
@RestController
public class FileController extends FileBaseController {
    @Autowired
    private WebConfig config;

    @PostMapping(value = "/api/file/delete")
    public Response<String> delete(@RequestParam String filename, String path) throws IOException {
        path = checkPath(path);
        String newDirectory = config.getFileUploadPath() + path;
        Files.deleteIfExists(Paths.get(newDirectory, filename));
        return Response.ok("删除成功");
    }

    @GetMapping("/api/file/download/{filename:.+}")
    public void fileDownload(@PathVariable String filename, String path, HttpServletResponse response) {
        try {
            filename = UnicodeUtil.unicodeToUtf8(filename);
            path = UnicodeUtil.unicodeToUtf8(path);

            path = checkPath(path);
            String newDirectory = config.getFileUploadPath() + path;
            Path tempPath = Paths.get(newDirectory, filename);
            FileSystemResource fileSystemResource = new FileSystemResource(tempPath.toString());
            responseTo(fileSystemResource.getFile(), response);
        } catch (Exception e) {
            log.error(Throwables.getStackTraceAsString(e));
        }
    }

    @PostMapping("/api/file/upload")
    public Response<String> fileUpload(@RequestParam("uploadFile") MultipartFile uploadFile, String path) throws Exception {
        if (uploadFile.isEmpty()) {
            throw new ServiceException("uploadFile is null");
        }
        path = checkPath(path);
        String fileName = uploadFile.getOriginalFilename();
        String key = path + fileName;
        try {
            if (isLock(key)) {
                return Response.fail("存在同样的文件在上传");
            }
            String newDirectory = createChildFile(config.getFileUploadPath(), path);
            Files.deleteIfExists(Paths.get(newDirectory, fileName));
            Files.copy(uploadFile.getInputStream(), Paths.get(newDirectory, fileName));
            return Response.ok(fileName);
        } finally {
            lockMap.remove(key);
        }
    }

}
