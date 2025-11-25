package com.hmdp;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
public class StringRedisTemplateTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testBitMap() {
        String key = "test:bit";
        stringRedisTemplate.opsForValue().setBit(key, 24L, true);
        Boolean bit = stringRedisTemplate.opsForValue().getBit(key, 24L);
        System.out.println(bit);
    }

    @Test
    public void testBitField() {
        String key = "test:bit";

        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        System.out.println(dayOfMonth);

        Boolean bit = stringRedisTemplate.opsForValue().getBit(key, 24L);
        System.out.println(bit);

        List<Long> results = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        System.out.println(results);
    }

    @Test
    public void loadSignData() {
        String key = "sign:10:202511";
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 2, true);
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 3, true);
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 4, true);
    }
}
