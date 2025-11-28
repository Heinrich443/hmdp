package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.验证手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2.生成6位随机数验证码
        String code = RandomUtil.randomNumbers(6);

        // 3.存入Redis，TTL 2分钟
        String key = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("发送短信验证码成功，验证码：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        // 1.验证手机号
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2.验证码校验
        String key = LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(key);
        String code = loginFormDTO.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3.查询或创建用户（手机号自动注册）
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createByPhone(phone);
        }

        // 4.生成 UUID token
        String token = LOGIN_USER_KEY + UUID.randomUUID().toString();
        // 5.用户信息存入 Redis Hash，TTL 30 分钟
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setFieldValueEditor((name, value) -> value.toString()));
        stringRedisTemplate.opsForHash().putAll(token, map);
        stringRedisTemplate.expire(token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 6.返回 token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取当前日期
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 3.生成key
        String key = USER_SIGN_KEY + userId.toString() + date;
        // 4.签到
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        Boolean bit = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
        log.debug("签到信息如下：{}", bit);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取当前日期
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 3.生成key
        String key = USER_SIGN_KEY + userId.toString() + date;
        // 4.查询连续签到天数
        // Boolean bit = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
        // log.debug("签到信息如下：{}", bit);

        List<Long> results = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        if (results == null || results.isEmpty()) {
            return Result.ok(0);
        }

        Long result = results.get(0);
        if (result == null || result == 0) {
            return Result.ok(0);
        }

        int count = 0;
        while (result != 0) {
            if ((result & 1) == 1) {
                count ++;
            } else {
                // return Result.ok(count);
                break;
            }

            result >>>= 1;
        }

        // 5.返回
        return Result.ok(count);
        // return Result.ok(0);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            UserHolder.removeUser();
        }

        String key = LOGIN_USER_KEY + request.getHeader("authorization");
        stringRedisTemplate.delete(key);

        return Result.ok();
    }

    private User createByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
