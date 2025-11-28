package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断关注or取关
        String key = FOLLOW_KEY + userId;
        if (!isFollow) {
            // 关注，新增数据
            Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
            if (count > 0) {
                return Result.ok();
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean flag = save(follow);

            if (flag) {
                // 把关注用户的id放入redis的set集合
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            // 取关，删除
            boolean flag = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if (flag) {
                // 把关注用户的id从redis的set集合中移除
                stringRedisTemplate.opsForSet().remove(key, id);
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;
        // 查询是否关注
        Boolean flag = stringRedisTemplate.opsForSet().isMember(key, id.toString());
        // 返回
        return Result.ok(flag);
    }

    @Override
    public Result followCommon(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 求交集
        String userKey = FOLLOW_KEY + userId;
        String key = FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, key);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集，返回空集合
            return Result.ok(Collections.emptyList());
        }
        // 解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户
        List<UserDTO> users = userService.query().in("id", ids).list().
                stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        // 返回
        return Result.ok(users);
    }
}
