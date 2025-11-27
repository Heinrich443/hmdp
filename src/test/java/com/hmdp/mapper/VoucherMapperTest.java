package com.hmdp.mapper;

import com.hmdp.entity.Voucher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
public class VoucherMapperTest {

    @Resource
    private VoucherMapper voucherMapper;

    @Test
    public void testGetVouchers() {
        List<Voucher> vouchers = voucherMapper.getVouchers(2L);
        System.out.println(vouchers);
    }
}
