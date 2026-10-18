package com.hmdp.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOPTYPE_KEY + "list";

        String shoptypejson = stringRedisTemplate.opsForValue().get(key);
        //1.从Rediss中查询数据
        if(StrUtil.isNotBlank(shoptypejson)){
            //2.查到，则返回数据
            List<ShopType> typeList = JSONUtil.toList(shoptypejson,ShopType.class);
            return Result.ok(typeList);
        }

        //3.未查到，进入数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //5.未查到，报错
        if(typeList == null) {
            return Result.fail("商户列表不存在");
        }
        //4.查到，回写到Rediss中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList),CACHE_SHOP_TTL, TimeUnit.MINUTES);



        return Result.ok(typeList);
    }
}
