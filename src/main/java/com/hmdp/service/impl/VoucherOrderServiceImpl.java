package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisWorker redisWorker;

    @Override
    public Result seckillVoucher(Long id) {

        //1.查询优惠券
        SeckillVoucher voucher = iSeckillVoucherService.getById(id);
        //2.判断秒杀是否开始

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() <1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(id);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long id) {
        //一人一单()
        Long userId = UserHolder.getUser().getId();

        
            //查询订单
            long count = query().eq("user_id", userId).eq("voucher_id", id).count();
            //判断是否存在
            if (count > 0) {
                return Result.fail("一人只能购买一单");
            }

            //5.扣减库存
            boolean success = iSeckillVoucherService.update().setSql("stock=stock -1")
                    .eq("voucher_id", id).gt("stock", 0).update();
            if (!success) {
                return Result.fail("扣取失败");
            }
            //6.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();

            //6.1订单id
            Long orderid = redisWorker.nextId("order");
            voucherOrder.setId(orderid);
            //6.2用户id

            voucherOrder.setUserId(userId);
            //6.3代金券id
            voucherOrder.setVoucherId(id);
            save(voucherOrder);
            return Result.ok(orderid);
        }
    
}
