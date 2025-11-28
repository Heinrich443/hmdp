package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherOrderListener {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = "hmdp.order")
    public void listenVoucherOrder(VoucherOrder voucherOrder) {
        log.info("收到订单消息：{}", voucherOrder);
        handleVoucherOrder(voucherOrder);
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 创建 key
        Long userId = voucherOrder.getUserId();
        String key = "lock:order:" + userId;
        // 获取分布式锁
        /*boolean isLock = redissonClient.getLock(key).tryLock();
        if (!isLock) {
            // 获取锁失败，返回错误
            log.error("不允许重复下单");
            return;
        }

        voucherOrderService.createVoucherOrder(voucherOrder);*/
        RLock lock = redissonClient.getLock(key);
        boolean isLock = false;
        try {
            isLock = lock.tryLock();
            if (!isLock) {
                // 获取锁失败，返回错误
                log.error("不允许重复下单，userId: {}", userId);
                return;
            }
            // 处理订单
            voucherOrderService.createVoucherOrder(voucherOrder);
            log.info("订单处理成功，订单id: {}", voucherOrder.getId());
        } catch (Exception e) {
            log.error("处理订单异常，订单id: {}", voucherOrder.getId(), e);
        } finally {
            // 释放锁
            if (isLock && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
