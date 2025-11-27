package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final long COUNT_BITS = 32L;

    public Long nextId(String prefix) {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        // 计算时间戳
        long end = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = end - BEGIN_TIMESTAMP;
        // 拼接key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "incr:" + prefix + ":" + date;
        // 获取序列号，序列号 + 1
        Long increment = stringRedisTemplate.opsForValue().increment(key, 1L);
        // 返回id
        return (timestamp << COUNT_BITS) | increment;
    }
}
