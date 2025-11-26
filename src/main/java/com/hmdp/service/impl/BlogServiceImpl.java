package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IFollowService followService;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 保存blog到数据库
        blog.setUserId(userId);
        boolean flag = save(blog);
        if (!flag) {
            // 保存失败，返回错误信息
            return Result.fail("新增笔记失败！");
        }

        // 保存成功，推送给粉丝
        String value = blog.getId().toString();
        // 查询所有粉丝
        List<Long> ids = followService.query().eq("follow_user_id", userId).select("user_id").list().
                stream().map(Follow::getUserId).collect(Collectors.toList());

        ids.forEach(id -> {
            String key = "feed:" + id;
            stringRedisTemplate.opsForZSet().add(key, value, System.currentTimeMillis());
        });

        return Result.ok(value);
    }

    @Override
    public Result likeBlog(Long id) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断是否点赞
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞，点赞
            // 存入数据库 liked + 1
            boolean flag = update().setSql("liked = liked + 1").eq("id", id).update();
            if (flag) {
                // 将 id 存入 redis
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞，取消点赞
            // 数据库 liked - 1
            boolean flag = update().setSql("liked = liked - 1").eq("id", id).update();
            if (flag) {
                // 将 id 从 zset 中 移除
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 查询数据库，按 liked 降序排列，实现分页
        Page<Blog> page = query().orderByDesc("liked").page(new Page<>(current, 10));
        List<Blog> blogs = page.getRecords();
        blogs.forEach(blog -> {
            queryUser(blog);
            queryIsLike(blog);
        });
        // 封装数据返回
        return Result.ok(blogs);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = query().eq("id", id).one();
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }

        queryUser(blog);
        queryIsLike(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // "blog:liked:" + id
        String key = "blog:liked:" + id;
        Set<String> tuples = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        String join = StrUtil.join(",", tuples);

        List<UserDTO> users = userService.query().in("id", tuples).last("order by field(id," + join + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 查询当前登录用户
        Long userId = UserHolder.getUser().getId();
        // key "feed:" + userId
        String key = "feed:" + userId;
        // 查询blogs ZREVRANGE key min max limit offset count
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        // 判断 blogs 是否为空
        if (tuples == null || tuples.isEmpty()) {
            // 为空，返回 Result.ok();
            return Result.ok();
        }
        // 不为空，解析数据 minTime（时间戳） count（offset）并拼接ids
        Long minTime = 0L;
        int count = 0;
        List<Long> ids = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String id = tuple.getValue();
            long score = tuple.getScore().longValue();
            ids.add(Long.valueOf(id));
            if (score == minTime) {
                count ++;
            } else {
                minTime = score;
                count = 0;
            }
        }

        // 从数据库中查询 blogs
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + StrUtil.join(",", ids) + ")").list();
        // 遍历 blogs，添加 icon name isLike 字段
        for (Blog blog : blogs) {
            queryUser(blog);
            queryIsLike(blog);
        }
        // 返回 ScrollResult
        ScrollResult result = new ScrollResult();
        result.setOffset(count);
        result.setMinTime(minTime);
        result.setList(blogs);
        return Result.ok(result);
    }

    public void queryUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    public void queryIsLike(Blog blog) {
        // 获取当前用户
        if (UserHolder.getUser() == null) {
            // 当前用户为 null 则不进行点赞与否的查询
            return;
        }
        // 查询是否点赞
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, blog.getUserId().toString());
        blog.setIsLike(score != null);
    }
}
