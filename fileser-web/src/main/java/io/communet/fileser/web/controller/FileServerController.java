package io.communet.fileser.web.controller;

import com.google.common.base.Throwables;
import io.communet.fileser.common.exception.ServiceException;
import io.communet.fileser.common.vo.Response;
import io.communet.fileser.web.configuration.WebConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
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
    public Response<String> fileUpload(@RequestParam("uploadFile") MultipartFile uploadFile,String path,String isUpdate) throws Exception{
        if (uploadFile.isEmpty()) {
            throw new ServiceException("uploadFile is null");
        }
        path = checkPath(path);
        String fileName = uploadFile.getOriginalFilename();
        String key = path + fileName;
        try{
            if( isLock(key) ){
                return Response.fail("存在同样的文件在上传");
            }
            String newDirectory = config.getFileUploadPath() + path;
            String beforePath = config.getFileUploadPath();
            String[] paths = path.split("/");
            for (String p : paths) {
                beforePath = beforePath + "/" + p;
                if( !Files.exists(Paths.get(beforePath) ) ){
                    Files.createDirectory(Paths.get(beforePath));
                 }
            }
            //如果isUpdate等于1，需要判断文件是否存在，存在的话，文件合并
            if( isUpdate != null && isUpdate.equals("1") ){
                if( Files.exists(Paths.get(newDirectory , fileName) ) ){
                    Files.write(Paths.get(newDirectory , fileName), uploadFile.getBytes(), StandardOpenOption.APPEND);
                    return Response.ok(fileName);
                }
            }
            Files.deleteIfExists(Paths.get(newDirectory, fileName));
            Files.copy(uploadFile.getInputStream(), Paths.get(newDirectory , fileName));
            return Response.ok(fileName);
        }finally {
            lockMap.remove(key);
        }

    }

    @PostMapping(value = "/api/file/delete")
    public Response<String>  delete(@RequestParam String filename, String path ) throws IOException{
        path = checkPath(path);
        String newDirectory = config.getFileUploadPath() + path;
        Files.deleteIfExists(Paths.get(newDirectory, filename));
        return Response.ok("删除成功");
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
                if( Files.exists( Paths.get(newDirectory, filename) ) ){
                    Files.copy(Paths.get(newDirectory, filename), tempPath);
                }
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
