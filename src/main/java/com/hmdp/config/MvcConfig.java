package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);


        //登录拦截器
        registry.addInterceptor(new LoginInterceptor()).
               excludePathPatterns(
                       "/shop/**", //** 全部放行
                       "/blog/hot",
                       "/voucher/**",
                       "shop-type/**",
                       "/upload/**",
                       "/user/code",
                       "/user/login",
                       "/error"
               ).order(1);



    }
}
