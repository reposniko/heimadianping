package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
        @Resource
        private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //生成随机六位验证码
        String code =  RandomUtil.randomNumbers(6);
        //保存验证码到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误!");
        }

        //校验验证码 从REdis中获取并校验

        Object cachdeCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+ phone);
        String code = loginForm.getCode();
        //不一致，报错
        if(!cachdeCode.equals(code)  || cachdeCode == null){
            return  Result.fail("验证码错误!");
        }
        //一致
        User user = query().eq("phone",phone).one();
        //判断用户是否存在
        if(user == null){
            user =  creatUserWithPhone(phone);
        }
        //不存在，创建并保存

        //保存用户信息至session
        //7.1生成一个token作为登录令牌
        String token =  UUID.randomUUID().toString();
        //7.2将User转储为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user , UserDTO.class);
        //7.3存储
        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenkey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash()
                .putAll(tokenkey,userMap);
        //7.4 设置有效期
        stringRedisTemplate.expire(tokenkey,LOGIN_USER_TTL,TimeUnit.SECONDS);
        session.setAttribute("user",userMap );
        return Result.ok(token);

    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(4));
        save(user);
        return user;
    }


}
