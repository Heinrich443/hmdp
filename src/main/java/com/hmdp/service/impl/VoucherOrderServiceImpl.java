package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker idWorker;

    // @Resource
    // private RedissonClient redissonClient;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    // private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 线程池
    /*private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 在当前类初始化完毕后执行以下方法
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }*/

    /*private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    /*private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 创建 key
        Long userId = voucherOrder.getUserId();
        String key = "lock:order:" + userId;
        // 获取分布式锁
        boolean isLock = redissonClient.getLock(key).tryLock();
        if (!isLock) {
            // 获取锁失败，返回错误
            log.error("不允许重复下单");
            return;
        }

        proxy.createVoucherOrder(voucherOrder);
    }*/

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 执行 lua 脚本 1.检查库存 2.防重复下单 3.扣减库存
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), voucherId.toString(), userId.toString());
        // 返回 1 或 2，库存不足或重复下单
        if (result != 0) {
            return Result.ok(result == 1 ? "库存不足" : "不可重复下单");
        }
        // 返回 0
        // 生成订单id
        Long nextId = idWorker.nextId("order");
        // 创建 VoucherOrder 对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(nextId);
        voucherOrder.setUserId(userId);
        // 将订单放入消息队列，异步处理订单
        String queueName = "hmdp.order";
        rabbitTemplate.convertAndSend(queueName, voucherOrder);
        log.info("订单消息已发送到队列，订单id: {}, 用户id: {}, 优惠券id: {}", nextId, userId, voucherId);
        // 将订单放入消息队列，异步处理订单
        // orderTasks.add(voucherOrder);
        // 保存代理对象（成员变量）
        // proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单id
        return Result.ok(voucherId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单检查
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 1) {
            log.error("不允许重复下单");
            return;
        }

        // 扣减库存
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1").
                eq("voucher_id", voucherId).gt("stock", 0).update();

        if (!update) {
            log.error("库存不足");
            return;
        }

        // 保存订单
        save(voucherOrder);
    }
}
