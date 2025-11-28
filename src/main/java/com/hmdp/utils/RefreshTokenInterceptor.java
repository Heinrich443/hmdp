package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@RequiredArgsConstructor
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中获取token
        String token = request.getHeader("authorization");
        // 2.判断token是否为空
        if (StrUtil.isBlank(token)) {
            // 为空，放行
            return true;
        }

        // 3.判断用户信息是否存在
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map.isEmpty()) {
            // 不存在，放行
            return true;
        }

        // 4.存在，将用户信息存入ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
