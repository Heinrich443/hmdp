package com.hmdp.utils;

import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class CacheClientTest {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IShopService shopService;

    @Resource
    private IUserService userService;

    @Test
    public void testSet() {
        User user = new User();
        user.setNickName("aaaaa");
        cacheClient.set("test:cache:1", user, 2L, TimeUnit.MINUTES);
    }

    @Test
    public void testSetWithLogicalExpire() {
        User user = new User();
        user.setNickName("aaaaa");
        cacheClient.setWithLogicalExpire("test:cache:2", user, 2L, TimeUnit.SECONDS);
    }

    @Test
    public void testQueryWithPassThrough1() {
        User user = cacheClient.queryWithPassThrough("test:cache:", 1, User.class, userService::getById, 2L, TimeUnit.MINUTES);
        System.out.println(user);
    }

    @Test
    public void testQueryWithPassThrough2() {
        Shop shop = cacheClient.queryWithPassThrough("test:cache:", 15, Shop.class, shopService::getById, 2L, TimeUnit.MINUTES);
        System.out.println(shop);
    }

    @Test
    public void testQueryWithLogicalExpire1() {
        User user = cacheClient.queryWithLogicalExpire("test:cache:", 2, User.class, userService::getById, 2L, TimeUnit.MINUTES);
        System.out.println(user);
    }

    @Test
    public void testQueryWithLogicalExpire2() {
        User user = cacheClient.queryWithLogicalExpire("test:cache:", 1, User.class, userService::getById, 2L, TimeUnit.MINUTES);
        System.out.println(user);
    }
}
