package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key  = CACHE_SHOP_KEY + id;
        //1。从rediss查询数据
        String shopJson =  stringRedisTemplate.opsForValue().get(key);
        //2.判断rediss是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson , Shop.class);
            return Result.ok(shop);

        };
        //判断是否为空值
        if(shopJson != null){
            return Result.fail("店铺信息不存在！");
        }
        //4.不存在，查询数据库
        Shop shop = getById(id);
        //5.不存在，返回错误
        if(shop==null){
            //将空值写入Rediss
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return  Result.fail("商户不存在");
        }

        //存在则插入到Rediss中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //设置存放时间
        return  Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //1.更新数据库
        Long id = shop.getId();
        updateById(shop);
        if(id ==null){
            Result.fail("店铺id不能为空");
        }
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
