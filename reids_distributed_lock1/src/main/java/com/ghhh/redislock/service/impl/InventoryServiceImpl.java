package com.ghhh.redislock.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.IdUtil;
import com.ghhh.redislock.mylock.DistributedLockFactory;
import com.ghhh.redislock.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Value("${server.port}")
    private String port;
    //利用工厂模式调用自研锁
    @Autowired
    private DistributedLockFactory distributedLockFactory;
    @Autowired
    private Redisson redisson;

    @Override
    public String saveByRedisson() {
        RLock redissonLock = redisson.getLock("ghhhRedisLock");
        String retMessage = "";
        redissonLock.lock();
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
            if (redissonLock.isLocked() && redissonLock.isHeldByCurrentThread()){
                redissonLock.unlock();
            }
        }
        return retMessage+"\t"+"服务端口号"+port;
    }

    //=============================================================================================================//

    //v8.0 加自动续期功能 在RedisDistributedLock类中加renewExpire()方法
    public String save() {
        Lock myRedisLock = distributedLockFactory.getDistributedLock("redis");
        String retMessage = "";
        myRedisLock.lock();
        try{
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            if (inventoryNumber > 0){
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品,库存剩余:"+inventoryNumber;
                System.out.println(retMessage+"\t"+"服务端口号"+port);
                //为测试自动过期，sleep120s
                //try {TimeUnit.SECONDS.sleep(120);} catch (InterruptedException e) {e.printStackTrace();}
            }else {
                retMessage = "商品卖完了,o(╥﹏╥)o";
            }
        }finally {
            myRedisLock.unlock();
        }
        return retMessage+"\t"+"服务端口号"+port;
    }


//=============================================================================================================//


    /*
        v7.0 利用hash结构解决可重入性问题，在hash的 key val value，value如果有锁就加一
        用DistributedLockFactory工厂模式调用自己开发的锁getDistributedLock
        RedisDistributedLock自己开发的锁实现Lock，调用向外暴露的lock()，unlocl()
        lock中调用trylock()，在trylock()中首先判断在hash结构中是否没有工厂中定义的lockname或者是否有lockname和当前线程的uuidValue
        如果没有lockname则创建，如果有lockname和uuidvalue则uuidvalue后的value加一（证明发生了重入问题）
        等待锁--自旋调用
        unlock()中判断hash的value是否为0如果为0则返回null，如果不为0则自减为0结束
        存在问题：没有自动续期功能
        
     */
    /*public String save() {
        Lock myRedisLock = distributedLockFactory.getDistributedLock("redis");
        String retMessage = "";
        myRedisLock.lock();
        try{
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            if (inventoryNumber > 0){
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品,库存剩余:"+inventoryNumber;
                System.out.println(retMessage+"\t"+"服务端口号"+port);
                testReEntry();
            }else {
                retMessage = "商品卖完了,o(╥﹏╥)o";
            }
        }finally {
            myRedisLock.unlock();
        }
        return retMessage+"\t"+"服务端口号"+port;
    }*/
    /*private void testReEntry()//用在V7.0版本程序作为测试可重入性
    {
        Lock redisLock = distributedLockFactory.getDistributedLock("redis");
        redisLock.lock();
        try
        {
            System.out.println("===========测试可重入锁========");
        }finally {
            redisLock.unlock();
        }
    }*/

//=============================================================================================================//

    /*
        v6.0 加入lua脚本捏合delete和判断操作使它们变为原子性操作
        存在问题：可重入性问题
     */
    /*public String save(){
        String retMessage = "";
        String key = "GhhhRedisLock";
        String value = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();
        //自旋的进行重新调用
        while(!stringRedisTemplate.opsForValue().setIfAbsent(key, value,30L,TimeUnit.SECONDS)){
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
            //unredislock();
            //改进点，修改为Lua脚本的redis分布式锁调用，必须保证原子性，参考官网脚本案例
            String luaScript =
                    "if redis.call('get',KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del',KEYS[1]) " +
                            "else " +
                            "return 0 " +
                            "end";
            stringRedisTemplate.execute(new DefaultRedisScript(luaScript,Boolean.class), Arrays.asList(key),value);
        }
        return retMessage+"\t"+"服务端口号"+port;
    }*/

//=============================================================================================================//

    /*
        v5.0 判断加锁与解锁是不是同一个客户端，同一个才行，自己只能删除自己的锁，不误删他人的
        存在问题：delete和判断操作不是原子性的
     */
    /*public String save(){
        String retMessage = "";
        String key = "GhhhRedisLock";
        String value = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();
        //自旋的进行重新调用
        while(!stringRedisTemplate.opsForValue().setIfAbsent(key, value,30L,TimeUnit.SECONDS)){
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
            //改进点，只能删除属于自己的key，不能删除别人的
            // v5.0判断加锁与解锁是不是同一个客户端，同一个才行，自己只能删除自己的锁，不误删他人的
            if (stringRedisTemplate.opsForValue().get(key).equalsIgnoreCase(value)){
                stringRedisTemplate.delete(key);
            }
        }
        return retMessage+"\t"+"服务端口号"+port;
    }*/

//=============================================================================================================//

    /*
        v4.0 在锁上加过期时间
        存在问题：stringRedisTemplate.delete(key);只能自己删除自己的锁，不可以删除别人的，需要添加判断
    是否是自己的锁来进行操作
     */
    /*public String save(){
        String retMessage = "";
        String key = "GhhhRedisLock";
        String value = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();
        //自旋的进行重新调用
        //改进点：加锁和过期时间设置必须同一行，保证原子性
        while(!stringRedisTemplate.opsForValue().setIfAbsent(key, value,30L,TimeUnit.SECONDS)){
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
    }*/

//=============================================================================================================//

    /*
      v3.0  用setnx的方式进行加锁
       存在问题：部署了微服务的Java程序机器挂了，代码层面根本没有走到finally这块，
     * 没办法保证解锁(无过期时间该key一直存在)，这个key没有被删除，需要加入一个过期时间限定key
     */
    /*public String save(){
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
    }*/

//=============================================================================================================//

    /*
        V2.0 用lock单独加到每一个微服务
        存在问题：单机版加锁配合Nginx和Jmeter压测后，不满足高并发分布式锁的性能要求，出现超卖
     */
    /*private Lock lock = new ReentrantLock();
    @Override
    public String save() {
        String retMessage = "";
        lock.lock();
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
            lock.unlock();
        }
        return retMessage+"\t"+"服务端口号"+port;
    }*/

//=============================================================================================================//
    /*
        v1.0 无锁状态
     */
    /*@Override
    public String save() {
        String retMessage = "";
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            if (inventoryNumber > 0){
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品,库存剩余:"+inventoryNumber;
                System.out.println(retMessage+"\t"+"服务端口号"+port);
            }else {
                retMessage = "商品卖完了,o(╥﹏╥)o";
            }
        return retMessage+"\t"+"服务端口号"+port;
    }*/
}
