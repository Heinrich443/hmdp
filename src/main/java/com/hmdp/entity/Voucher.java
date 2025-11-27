package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private String title;

    private String subTitle;

    private String rules;

    private Long payValue;

    private Long actualValue;

    private Boolean type;

    private Boolean status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Integer stock;

    @TableField(exist = false)
    private LocalDateTime beginTime;

    @TableField(exist = false)
    private LocalDateTime endTime;
}
