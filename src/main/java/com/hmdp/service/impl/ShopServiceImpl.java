package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;
import static com.hmdp.utils.SystemConstants.MAX_PAGE_SIZE;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return  Result.fail("店铺id不能为空！");
        }

        updateById(shop);
        Long shopId = shop.getId();
        String key = CACHE_SHOP_KEY + shopId;
        stringRedisTemplate.delete(key);

        return Result.ok();
    }

    @Override
    public Result getByName(String name, Integer current) {
        Page<Shop> page = query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, DEFAULT_PAGE_SIZE));
            // 返回
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数 from end
        int from = (current - 1) * DEFAULT_BATCH_SIZE;
        int end = current * DEFAULT_BATCH_SIZE;

        // 3.查询redis，根据距离进行排序、分页。结果：shopId, distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key, GeoReference.fromCoordinate(x, y),
                new Distance(50000), RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        // 4.判断查询结果是否为空或达不到分页条数
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        // 5.解析出id
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> map = new HashMap<>(list.size());

        // 截取 from ~ end 的部分
        list.stream().skip(from).forEach(shop -> {
            String shopId = shop.getContent().getName();
            // 获取店铺id
            ids.add(Long.valueOf(shopId));
            // 获取距离
            map.put(shopId, shop.getDistance());
        });

        // 6.根据id查询Shop
        String strIds = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + strIds + ")").list();
        for (Shop shop: shops) {
            // 把店铺和距离合并在一起
            Distance distance = map.get(shop.getId().toString());
            shop.setDistance(distance.getValue());
        }

        // 7.返回店铺信息集合
        return Result.ok(shops);
    }
}
