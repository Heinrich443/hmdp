package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getType() {
        // 从缓存里查数据
        String cacheKey = CACHE_SHOP_TYPE_KEY + "*";
        Set<String> keys = stringRedisTemplate.keys(cacheKey);
        if (keys != null && !keys.isEmpty()) {
            List<String> list = stringRedisTemplate.opsForValue().multiGet(keys);
            if (list != null && !list.isEmpty()) {
                // 存在，返回
                List<ShopType> shopTypes = new ArrayList<>();
                for (String value: list) {
                    shopTypes.add(JSONUtil.toBean(value, ShopType.class));
                }
                return Result.ok(shopTypes);
            }
        }

        // 不存在，从数据库中取
        List<ShopType> types = query().orderByAsc("sort").list();
        if (types == null || types.isEmpty()) {
            // 数据库中没有数据，返回
            return Result.fail("查询店铺类型错误！");
        }

        for (ShopType type: types) {
            // 数据库中存在数据，写入缓存，返回
            String key = CACHE_SHOP_TYPE_KEY + type.getId();
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(type));
        }

        return Result.ok(types);
    }
}
