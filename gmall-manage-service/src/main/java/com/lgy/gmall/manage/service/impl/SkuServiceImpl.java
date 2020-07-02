package com.lgy.gmall.manage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.lgy.gmall.bean.PmsSkuAttrValue;
import com.lgy.gmall.bean.PmsSkuImage;
import com.lgy.gmall.bean.PmsSkuInfo;
import com.lgy.gmall.bean.PmsSkuSaleAttrValue;
import com.lgy.gmall.manage.mapper.PmsSkuAttrValueMapper;
import com.lgy.gmall.manage.mapper.PmsSkuImageMapper;
import com.lgy.gmall.manage.mapper.PmsSkuInfoMapper;
import com.lgy.gmall.manage.mapper.PmsSkuSaleAttrValueMapper;
import com.lgy.gmall.service.SkuService;
import com.lgy.gmall.util.RedisUtil;
import jdk.nashorn.internal.ir.CallNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.UUID;

@Service
public class SkuServiceImpl implements SkuService {

    @Autowired
    PmsSkuInfoMapper pmsSkuInfoMapper;
    @Autowired
    PmsSkuAttrValueMapper pmsSkuAttrValueMapper;
    @Autowired
    PmsSkuImageMapper pmsSkuImageMapper;
    @Autowired
    PmsSkuSaleAttrValueMapper pmsSkuSaleAttrValueMapper;
    @Autowired
    RedisUtil redisUtil;

    @Override
    public void saveSkuInfo(PmsSkuInfo pmsSkuInfo) {

        //插入skuInfo
        int i = pmsSkuInfoMapper.insertSelective(pmsSkuInfo);
        String skuId = pmsSkuInfo.getId();

        //插入平台属性关联
        List<PmsSkuAttrValue> skuAttrValueList = pmsSkuInfo.getSkuAttrValueList();
        for (PmsSkuAttrValue pmsSkuAttrValue : skuAttrValueList) {
            pmsSkuAttrValue.setSkuId(skuId);
            pmsSkuAttrValueMapper.insertSelective(pmsSkuAttrValue);
        }

        //插入销售属性关联
        List<PmsSkuSaleAttrValue> skuSaleAttrValueList = pmsSkuInfo.getSkuSaleAttrValueList();
        for (PmsSkuSaleAttrValue pmsSkuSaleAttrValue : skuSaleAttrValueList) {
            pmsSkuSaleAttrValue.setSkuId(skuId);
            pmsSkuSaleAttrValueMapper.insertSelective(pmsSkuSaleAttrValue);
        }

        //插入图片信息
        List<PmsSkuImage> skuImageList = pmsSkuInfo.getSkuImageList();
        for (PmsSkuImage pmsSkuImage : skuImageList) {
            pmsSkuImage.setSkuId(skuId);
            pmsSkuImageMapper.insertSelective(pmsSkuImage);
        }

    }

    public PmsSkuInfo getSkuByIdFromDb(String skuId) {
        //商品对象！
        PmsSkuInfo pmsSkuInfo1 = new PmsSkuInfo();
        pmsSkuInfo1.setId(skuId);
        PmsSkuInfo skuInfo = pmsSkuInfoMapper.selectOne(pmsSkuInfo1);

        //图片集合
        PmsSkuImage pmsSkuImage = new PmsSkuImage();
        pmsSkuImage.setSkuId(skuId);
        List<PmsSkuImage> select = pmsSkuImageMapper.select(pmsSkuImage);
        skuInfo.setSkuImageList(select);
        return skuInfo;
    }
    @Override
    public PmsSkuInfo getSkuById(String skuId) {

        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();

        //链接缓存
        Jedis jedis = redisUtil.getJedis();
        //查询缓存
        String skuKey = "sku:"+skuId+":info";
        String skuJson = jedis.get(skuKey);
        if(StringUtils.isNotBlank(skuJson)){
            pmsSkuInfo = JSON.parseObject(skuJson, PmsSkuInfo.class);
        }else{
            //如果缓存没有。则查询mysql
            //设置分布式锁
            String token = UUID.randomUUID().toString(); //使用value来标识自己的锁
            String OK = jedis.set("sku:" + skuId + ":lock", token, "nx", "px", 10*1000);//10秒过期时间
            if(StringUtils.isNotBlank(OK)&& OK.equals("Ok")){
                //设置成功，有权在10s内访问数据库
                pmsSkuInfo = getSkuByIdFromDb(skuId);
                //mysql查询存入redis
                if(pmsSkuInfo!=null){
                    jedis.set(skuKey, JSON.toJSONString(pmsSkuInfo));
                }else {
                    //数据库中不存在sku
                    //为了防止数据穿透，给redis设置一个null或者空的值。
                    jedis.setex(skuKey, 60*3,JSON.toJSONString(""));
                }

                //在访问MySql后，将mysql锁释放
                String locktoken = jedis.get("sku:" + skuId + ":lock");
                if(StringUtils.isNotBlank(locktoken)&&locktoken.equals(token)){
                    //用token确定删除的是自己的锁
                    jedis.del("sku:" + skuId + ":lock");
                }
            }else {
                //设置失败，自旋（改线程睡眠几秒以后，重新访问本方法）
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return getSkuById(skuId);
            }

        }

        jedis.close();

        return pmsSkuInfo;
    }

    @Override
    public List<PmsSkuInfo> getSkuSaleAttrValueListBySpu(String productId) {

        List<PmsSkuInfo> pmsSkuInfos = pmsSkuInfoMapper.selectSkuSaleAttrValueListBySpu(productId);
        return pmsSkuInfos;
    }

    @Override
    public List<PmsSkuInfo> getAllSku() {
        List<PmsSkuInfo> pmsSkuInfos = pmsSkuInfoMapper.selectAll();
        for (PmsSkuInfo pmsSkuInfo : pmsSkuInfos) {
            String skuId = pmsSkuInfo.getId();

            PmsSkuAttrValue pmsSkuAttrValue = new PmsSkuAttrValue();
            pmsSkuAttrValue.setSkuId(skuId);
            List<PmsSkuAttrValue> select = pmsSkuAttrValueMapper.select(pmsSkuAttrValue);
            pmsSkuInfo.setSkuAttrValueList(select);
        }
        return pmsSkuInfos;
    }
}
