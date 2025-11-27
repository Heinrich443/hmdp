package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import jodd.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload/blog")
public class UploadController {

    @PostMapping
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            String originalFilename = image.getOriginalFilename();
            String fileName = createFileName(originalFilename);
            image.transferTo(new File(fileName));
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/delete")
    public Result deleteImage(@RequestParam("name") String image) {
        try {
            File file = new File(image);
            if (file.isDirectory()) {
                return Result.fail("错误的文件名称");
            }
            FileUtil.delete(file);
            return Result.ok();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String createFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File("/opt/homebrew/var/www/hmdp/imgs", StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
