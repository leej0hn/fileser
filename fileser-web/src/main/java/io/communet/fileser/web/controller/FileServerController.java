package io.communet.fileser.web.controller;

import com.google.common.base.Throwables;
import io.communet.fileser.common.exception.ServiceException;
import io.communet.fileser.common.vo.Response;
import io.communet.fileser.utils.UnicodeUtil;
import io.communet.fileser.web.configuration.WebConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>function:
 * <p>User: LeeJohn
 * <p>Date: 2017/06/15
 * <p>Version: 1.0
 */
@Slf4j
@RestController
public class FileServerController {
    private static String TEMP_DIR = "/temp";
    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private WebConfig config;

    private Map<String,Object> lockMap = new ConcurrentHashMap();
    private String charSet = "utf-8";

    @PostMapping("/api/text/upload")
    public Response<String> textUpload(String fileName,String content,String path,String isUpdate) throws Exception{
        fileName = UnicodeUtil.unicodeToUtf8(fileName);
        path = UnicodeUtil.unicodeToUtf8(path);
        content = UnicodeUtil.unicodeToUtf8(content);

        path = checkPath(path);
        String key = path + fileName;
        try{
            if( isLock(key) ){
                return Response.fail("存在同样的文本内容在上传");
            }
            String newDirectory = createChildFile(path);
            //如果isUpdate等于1，需要判断文件是否存在，存在的话，文件合并
            if( isUpdate != null && isUpdate.equals("1") ){
                if( Files.exists(Paths.get(newDirectory , fileName) ) ){
                    Files.write(Paths.get(newDirectory , fileName), content.getBytes(charSet), StandardOpenOption.APPEND);
                    return Response.ok(fileName);
                }
            }
            Files.write(Paths.get(newDirectory, fileName),content.getBytes(charSet));
            return Response.ok(fileName);
        }finally {
            lockMap.remove(key);
        }

    }

    @PostMapping(value = "/api/file/delete")
    public Response<String>  delete(@RequestParam String filename, String path ) throws IOException {
        path = checkPath(path);
        String newDirectory = config.getFileUploadPath() + path;
        Files.deleteIfExists(Paths.get(newDirectory, filename));
        return Response.ok("删除成功");
    }


    @GetMapping("/api/text/download")
    public Response<String> textDownload(String filename, String path , String isUpdate) throws Exception{
            path = checkPath(path);
            String key = path + filename;
            String newDirectory = config.getFileUploadPath() + path;
            Path filePath = Paths.get(newDirectory, filename);
            if( !Files.exists(filePath)){
                throw new ServiceException("文件不存在");
            }
            String content = new String(Files.readAllBytes(filePath),charSet);
            if ( isUpdate != null && isUpdate.equals("1")) {//更新
                try{
                    if( isLock(key) ){
                        throw new ServiceException("文件正在修改中");
                    }
                    Files.deleteIfExists(filePath);
                }finally {
                    lockMap.remove(key);
                }
            }
            return Response.ok(content);
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


    @PostMapping("/api/file/upload")
    public Response<String> fileUpload(@RequestParam("uploadFile") MultipartFile uploadFile, String path) throws Exception{
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
            String newDirectory = createChildFile(path);
            Files.deleteIfExists(Paths.get(newDirectory, fileName));
            Files.copy(uploadFile.getInputStream(), Paths.get(newDirectory , fileName));
            return Response.ok(fileName);
        }finally {
            lockMap.remove(key);
        }

    }

    @GetMapping("/api/file/download/{filename:.+}")
    public void fileDownload(@PathVariable String filename, String path , HttpServletResponse response) {
        try {
            filename = UnicodeUtil.unicodeToUtf8(filename);
            path = UnicodeUtil.unicodeToUtf8(path);

            path = checkPath(path);
            String newDirectory = config.getFileUploadPath() + path;
            Path tempPath = Paths.get(newDirectory, filename);
            FileSystemResource fileSystemResource = new FileSystemResource(tempPath.toString());
            responseTo(fileSystemResource.getFile(),response);
        }catch (Exception e){
            log.error(Throwables.getStackTraceAsString(e));
        }
    }


    private String createChildFile(String path) throws Exception{
        String newDirectory = config.getFileUploadPath() + path;
        String beforePath = config.getFileUploadPath();
        String[] paths = path.split("/");
        for (String p : paths) {
            beforePath = beforePath + "/" + p;
            if( !Files.exists(Paths.get(beforePath) ) ){
                Files.createDirectory(Paths.get(beforePath));
            }
        }
        return newDirectory;
    }

    public static void responseTo(File file, HttpServletResponse res) {  //将文件发送到前端
        res.setHeader("content-type", "application/octet-stream");
        res.setContentType("application/octet-stream");
        res.setHeader("Content-Disposition", "attachment;filename=" + file.getName());
        byte[] buff = new byte[500 * 1024];
        BufferedInputStream bis = null;
        OutputStream os = null;
        try {
            os = res.getOutputStream();
            bis = new BufferedInputStream(new FileInputStream(file));
            int i = bis.read(buff);
            while (i != -1) {
                os.write(buff, 0, buff.length);
                os.flush();
                i = bis.read(buff);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
