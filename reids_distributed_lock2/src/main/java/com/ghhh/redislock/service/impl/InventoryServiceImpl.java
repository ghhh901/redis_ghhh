package com.ghhh.redislock.service.impl;

import cn.hutool.core.util.IdUtil;
import com.ghhh.redislock.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Value("${server.port}")
    private String port;

    @Override
    public String save(){
        String retMessage = "";
        String key = "GhhhRedisLock";
        String value = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();
        //自旋的进行重新调用
        while(!stringRedisTemplate.opsForValue().setIfAbsent(key, value)){
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try{
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            if (inventoryNumber > 0){
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品,库存剩余:"+inventoryNumber;
                System.out.println(retMessage+"\t"+"服务端口号"+port);
            }else {
                retMessage = "商品卖完了,o(╥﹏╥)o";
            }
        }finally {
            stringRedisTemplate.delete(key);
        }
        return retMessage+"\t"+"服务端口号"+port;
    }
}
