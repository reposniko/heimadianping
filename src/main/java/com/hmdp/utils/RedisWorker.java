package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
/*
* 这是一个用于生成全局ID的生成器
*
*
* */
@Component
public class RedisWorker {
    private static  final  long BEGIN_TIMETANP =1790380600L;
    private static  final  int COUNT_BITS =32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime localDateTime = LocalDateTime.now();
        long nowSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMETANP;
        //生成序列号
        //获取当前日期
        String date = localDateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:DD"));
        //日期自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //拼接并返回

        return timestamp << COUNT_BITS | count;


    }


}
