package io.communet.fileser.web.controller;

import io.communet.fileser.common.exception.ServiceException;
import io.communet.fileser.common.vo.Response;
import io.communet.fileser.utils.UnicodeUtil;
import io.communet.fileser.web.configuration.WebConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * <p>function:
 * <p>User: LeeJohn
 * <p>Date: 2017/06/15
 * <p>Version: 1.0
 */
@Slf4j
//@RestController
public class FileServerController extends FileBaseController {
    private static String TEMP_DIR = "/temp";
    private String charSet = "utf-8";
    @Autowired
    private WebConfig config;
    @Autowired
    private ResourceLoader resourceLoader;

    @PostMapping(value = "/api/file/delete")
    public Response<String> delete(@RequestParam String filename, String path) throws IOException {
        path = checkPath(path);
        String newDirectory = config.getFileUploadPath() + path;
        Files.deleteIfExists(Paths.get(newDirectory, filename));
        return Response.ok("删除成功");
    }

    @GetMapping("/api/text/download")
    public Response<String> textDownload(String filename, String path, String isUpdate) throws Exception {
        path = checkPath(path);
        String key = path + filename;
        String newDirectory = config.getFileUploadPath() + path;
        Path filePath = Paths.get(newDirectory, filename);
        if (!Files.exists(filePath)) {
            throw new ServiceException("文件不存在");
        }
        String content = new String(Files.readAllBytes(filePath), charSet);
        if (isUpdate != null && isUpdate.equals("1")) {//更新
            try {
                if (isLock(key)) {
                    throw new ServiceException("文件正在修改中");
                }
                Files.deleteIfExists(filePath);
            } finally {
                lockMap.remove(key);
            }
        }
        return Response.ok(content);
    }

    @PostMapping("/api/text/upload")
    public Response<String> textUpload(String fileName, String content, String path, String isUpdate) throws Exception {
        fileName = UnicodeUtil.unicodeToUtf8(fileName);
        path = UnicodeUtil.unicodeToUtf8(path);
        content = UnicodeUtil.unicodeToUtf8(content);

        path = checkPath(path);
        String key = path + fileName;
        try {
            if (isLock(key)) {
                return Response.fail("存在同样的文本内容在上传");
            }
            String newDirectory = createChildFile(config.getFileUploadPath(), path);
            //如果isUpdate等于1，需要判断文件是否存在，存在的话，文件合并
            if (isUpdate != null && isUpdate.equals("1")) {
                if (Files.exists(Paths.get(newDirectory, fileName))) {
                    Files.write(Paths.get(newDirectory, fileName), content.getBytes(charSet), StandardOpenOption.APPEND);
                    return Response.ok(fileName);
                }
            }
            Files.write(Paths.get(newDirectory, fileName), content.getBytes(charSet));
            return Response.ok(fileName);
        } finally {
            lockMap.remove(key);
        }
    }

}
