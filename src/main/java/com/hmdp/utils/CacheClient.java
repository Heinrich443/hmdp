package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 拼接key
        String key = prefix + id;
        // 根据key查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            // 缓存命中，返回
            R obj = JSONUtil.toBean(json, type);
            return obj;
        }

        // 缓存命中空值，返回
        if (json != null) {
            return null;
        }

        // 缓存未命中
        // 查询数据库，判断店铺是否存在
        R result = dbFallBack.apply(id);
        if (result == null) {
            // 店铺不存在，在缓存中存入空值
            // stringRedisTemplate.opsForValue().set(key, "", 2L, TimeUnit.MINUTES);
            set(key, "", 2L, TimeUnit.MINUTES);
            return null;
        }

        // 店铺存在，存入缓存
        // stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result), time, unit);
        set(key, result, time, unit);
        // 返回店铺信息
        return result;
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    
    // 锁的标识前缀，使用UUID确保唯一性
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    
    // 释放锁的Lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    // 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R, ID> R queryWithLogicalExpire(String prefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 拼接key
        String key = prefix + id;
        // 根据key查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否命中
        if (StrUtil.isBlank(json)) {
            // 未命中，返回空
            // return queryWithPassThrough(prefix, id, type, dbFallBack, time, unit);
            return null;
        }

        // 命中，判断缓存是否过期
        RedisData result = JSONUtil.toBean(json, RedisData.class);
        // R r = JSONUtil.toBean(JSONUtil.toJsonStr(result.getData()), type);
        R r = JSONUtil.toBean((JSONObject) result.getData(), type);
        if (result.getExpireTime().isAfter(LocalDateTime.now())) {
            // 未过期，返回商铺信息
            return r;
        }
        // 过期，尝试获取互斥锁
        String lockKey = "cache:lock:" + id;
        String lockValue = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = tryLock(lockKey, lockValue);
        // 判断是否获得锁
        if (success) {
            // 是，开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 根据id查询数据库，将商铺信息写入缓存，并设置逻辑过期时间
                    R apply = dbFallBack.apply(id);
                    setWithLogicalExpire(key, apply, time, unit);
                } catch (Exception e) {
                    log.error("缓存重建失败", e);
                } finally {
                    // 释放互斥锁，使用Lua脚本确保原子性
                    unlock(lockKey, lockValue);
                    log.debug("释放互斥锁成功");
                }
            });
        }

        // 否，返回商铺信息
        return r;
    }

    /**
     * 释放锁，使用Lua脚本确保原子性，只有锁的持有者才能释放锁
     * @param key 锁的key
     * @param lockValue 锁的值（线程标识）
     */
    private void unlock(String key, String lockValue) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, 
            Collections.singletonList(key), 
            lockValue);
    }

    /**
     * 尝试获取锁
     * @param key 锁的key
     * @param lockValue 锁的值（线程标识）
     * @return 是否获取成功
     */
    public Boolean tryLock(String key, String lockValue) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, lockValue, 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
}
