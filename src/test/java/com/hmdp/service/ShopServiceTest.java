package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class ShopServiceTest {

    @Resource
    private IShopService shopService;

    @Resource
    private CacheClient cacheClient;

    @Test
    public void loadShopCache() {
        List<Shop> shops = shopService.query().list();
        shops.forEach(shop -> {
            cacheClient.setWithLogicalExpire("cache:shop:" + shop.getId(), shop, 2L, TimeUnit.MINUTES);
        });
    }
}
