package io.communet.fileser.web.controller;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @desc:
 * @author: LeeJohn
 * @date 2019-06-17 09:44
 */
public class FileBaseController {
    protected Map<String, Object> lockMap = new ConcurrentHashMap();

    protected static void responseTo(File file, HttpServletResponse res) {  //将文件发送到前端
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

    protected String checkPath(String path) {
        if (path == null) {
            path = "";
        } else if (!path.substring(0, 1).equals("/")) {
            path = "/" + path;
        }
        return path;
    }

    protected String createChildFile(String configFileUploadPath, String path) throws Exception {
        String newDirectory = configFileUploadPath + path;
        StringBuilder beforePath = new StringBuilder(configFileUploadPath);
        String[] paths = path.split("/");
        for (String p : paths) {
            beforePath = beforePath.append("/" + p);
            if (!Files.exists(Paths.get(beforePath.toString()))) {
                Files.createDirectory(Paths.get(beforePath.toString()));
            }
        }
        return newDirectory;
    }

    /**
     * @return true 是锁了，false是未锁
     */
    protected synchronized boolean isLock(String key) {
        boolean flag = false;
        if (lockMap.get(key) == null) {
            Object obj = new Object();
            lockMap.put(key, obj);
        } else {
            flag = true;
        }
        return flag;
    }

}
