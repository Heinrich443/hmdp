package com.hmdp.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 用于声明队列
 */
// @Configuration
public class RabbitMQConfig {

    /**
     * 声明订单队列
     * 队列名称：hmdp.order
     * durable: true 表示队列持久化
     */
    @Bean
    public Queue voucherOrderQueue() {
        return new Queue("hmdp.order", true);
    }
}

