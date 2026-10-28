package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;


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
    private RedissonClient redissonClient;

    @Resource
    private RedisWorker redisWorker;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //这里封装lua的对象被final修饰了，所以要在下方用一个static修饰对SECKIll_scrirt进行一个复杂的初始化
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<VoucherOrder>(1024 * 1024) {};

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
        @PostConstruct
    private void init(){

        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //后台不断取订单
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("order:" + userId);
        //获取锁
        boolean isylock = lock.tryLock();

        if(!isylock){
            //获取锁失败
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            //2.1 不为0，每购买资格
            return Result.fail(r == 1?"库存不足":"不能重复下单");
        }
        //2.2 为0，有购买资格，把下单信息保存到阻塞队列
        //2.4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //2.5订单id
        Long orderid = redisWorker.nextId("order");
        voucherOrder.setId(orderid);
        //2.6用户id
        voucherOrder.setUserId(userId);
        //6.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //2.7放入阻塞队列
        orderTasks.add(voucherOrder);
        //3.返回订单id

        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderid);
    }

//    @Override
//    public Result seckillVoucher(Long id) {
//
//        //1.查询优惠券
//        SeckillVoucher voucher = iSeckillVoucherService.getById(id);
//        //2.判断秒杀是否开始
//
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        //3.判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束");
//        }
//        //4.判断库存是否充足
//        if (voucher.getStock() <1) {
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
//
//        RLock lock = redissonClient.getLock("order:" + userId);
//
//        //获取锁
//        boolean isylock = lock.tryLock();
//
//        if(!isylock){
//            //获取锁失败
//            return Result.fail("一人仅允许下一单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(id);
//        } finally {
//            lock.unlock();
//        }
//
//    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单()
        Long userId = voucherOrder.getUserId();
            //查询订单
            long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            //判断是否存在(可以不写，其实)
            if (count > 0) {
            log.error("用户已经购买过一次！");
                return ;
            }
            //5.扣减库存
            boolean success = iSeckillVoucherService.update().setSql("stock=stock -1")
                    .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
            if (!success) {
                log.error("库存不足");
                return ;
            }
            //创建订单
            save(voucherOrder);

        }
    
}
