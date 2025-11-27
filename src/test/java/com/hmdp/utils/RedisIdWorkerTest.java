package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class RedisIdWorkerTest {

    @Resource
    private RedisIdWorker idWorker;

    @Test
    public void testNextId() {
        Long nextId = idWorker.nextId("order");
        System.out.println(nextId);
    }
}
