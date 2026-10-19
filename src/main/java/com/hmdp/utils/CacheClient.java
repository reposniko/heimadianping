package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate =stringRedisTemplate;

    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设逻辑时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID>R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){

        String key  = keyPrefix + id;
        //1。从rediss查询数据
        String json =  stringRedisTemplate.opsForValue().get(key);
        //2.判断rediss是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返
            return JSONUtil.toBean(json , type);

        };
        //判断是否为空值
        if(json != null){
            return null;
        }
        //4.不存在，查询数据库
        R r = dbFallback.apply(id);
        //5.不存在，返回错误
        if(r==null){
            //将空值写入Rediss
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return  null;
        }

        //存在则插入到Rediss中
        this.set(key,r,time,unit);
        //设置存放时间
        return  r;
    }
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID>R  queryWithLogicalExpire(String keyPrefix,ID id,Class<R>type,Function<ID,R> dbFallBack,Long time, TimeUnit unit){

        String key  = keyPrefix + id;
        //1。从rediss查询数据
        String json =  stringRedisTemplate.opsForValue().get(key);
        //2.判断rediss是否存在
        if(StrUtil.isBlank(json)){

            return null;

        }
        //4.命中，需要判断过期时间，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData() , type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1未过期，直接返回
            return r;
        }

        //5.2已过期，需要缓存重建

        //6.缓存过期
        //6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        //6.2判断是否获取锁成功
        if(isLock){
            //6.3成功，开启独立进程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()-> {
                //重建
                try {
                    //查询数据库
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }

            });
        }

        //6.4返回过期的缓存数据
        return r;

    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
