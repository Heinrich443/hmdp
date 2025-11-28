package com.hmdp;

import com.hmdp.entity.VoucherOrder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class MessageConverterTest {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testSendVoucherOrder() {
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(2L);
        voucherOrder.setId(2L);
        voucherOrder.setUserId(2L);
        String queueName = "test.queue";
        rabbitTemplate.convertAndSend(queueName, voucherOrder);
    }

    @Test
    // @RabbitListener(queues = "test.queue")
    public void testReceiveMassage(VoucherOrder voucherOrder) {
        System.out.println(voucherOrder);
    }
}
