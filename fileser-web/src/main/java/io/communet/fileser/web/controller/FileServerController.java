package io.communet.fileser.web.controller;

import com.google.common.base.Throwables;
import io.communet.fileser.common.exception.ServiceException;
import io.communet.fileser.common.vo.Response;
import io.communet.fileser.web.configuration.WebConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>function:
 * <p>User: LeeJohn
 * <p>Date: 2017/06/15
 * <p>Version: 1.0
 */
@Slf4j
@RestController()
public class FileServerController {
    private static String TEMP_DIR = "/temp";
    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private WebConfig config;

    private Map<String,Object> lockMap = new ConcurrentHashMap();

    @PostMapping("/api/file/upload")
    public Response<String> fileUpload(@RequestParam("uploadFile") MultipartFile uploadFile,String path) throws Exception{
        if (uploadFile.isEmpty()) {
            throw new ServiceException("uploadFile is null");
        }
        path = checkPath(path);
        String fileName = uploadFile.getOriginalFilename();
        String newDirectory = config.getFileUploadPath() + path;
        if( !Files.exists(Paths.get(newDirectory) ) ){
            Files.createDirectory(Paths.get(newDirectory));
        }
        Files.deleteIfExists(Paths.get(newDirectory, fileName));
        Files.copy(uploadFile.getInputStream(), Paths.get(newDirectory , fileName));
        return Response.ok(fileName);
    }

    @GetMapping("/api/file/download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<?> fileDownload(@PathVariable String filename, String path , String isUpdate) {
        try {
            path = checkPath(path);
            String key = path + filename;
            String newDirectory = config.getFileUploadPath() + path;
            Path tempPath = Paths.get(newDirectory, filename);
            if ( isUpdate != null && isUpdate.equals("1")) {//更新
                if( isLock(key) ){
                    return null;
                }
                String tempDirectory = config.getFileUploadPath() + TEMP_DIR;
                tempPath = Paths.get(tempDirectory, filename);
                if( !Files.exists( Paths.get(tempDirectory) ) ){
                    Files.createDirectory(Paths.get(tempDirectory));
                }
                Files.deleteIfExists(tempPath);
                Files.copy(Paths.get(newDirectory, filename), tempPath);
                Files.deleteIfExists(Paths.get(newDirectory, filename));
                lockMap.remove(key);
            }
            FileSystemResource fileSystemResource = new FileSystemResource(tempPath.toString());
            return ResponseEntity.ok(fileSystemResource);
        }catch (Exception e){
            log.error(Throwables.getStackTraceAsString(e));
            return null;
        }
    }

    @GetMapping("/api/file/delete/{filename:.+}")
    @ResponseBody
    public Response<String>  delete(@PathVariable String filename, String path ) throws IOException{
        path = checkPath(path);
        String newDirectory = config.getFileUploadPath() + path;
        Files.deleteIfExists(Paths.get(newDirectory, filename));
        return Response.ok("删除成功");
    }

    private String checkPath(String path){
        if (path == null) {
            path = "";
        }else if( !path.substring(0,1).equals("/") ){
            path = "/" + path;
        }
        return path;
    }

    /**
     * @return true 是锁了，false是未锁
     */
    private synchronized boolean isLock(String key){
        boolean flag = false;
        if( lockMap.get(key) == null ){
            Object obj = new Object();
            lockMap.put(key,obj);
        }else{
            flag = true;
        }
        return flag;
    }

}
