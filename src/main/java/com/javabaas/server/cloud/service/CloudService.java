package com.javabaas.server.cloud.service;

import com.javabaas.server.admin.entity.App;
import com.javabaas.server.admin.service.AppService;
import com.javabaas.server.cloud.entity.CloudRequest;
import com.javabaas.server.cloud.entity.CloudResponse;
import com.javabaas.server.cloud.entity.CloudSetting;
import com.javabaas.server.cloud.entity.JBRequest;
import com.javabaas.server.common.entity.SimpleCode;
import com.javabaas.server.common.entity.SimpleError;
import com.javabaas.server.common.entity.SimpleResult;
import com.javabaas.server.user.entity.BaasUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Staryet on 15/9/15.
 */
@Service
public class CloudService {

    @Autowired
    private RestTemplate rest;
    @Autowired
    private AppService appService;

    public SimpleResult cloud(String appId, String plat, String functionName, BaasUser user, boolean isMaster, Map<String, String>
            requestParams, String body) {
        //准备请求数据
        CloudRequest cloudRequest = new CloudRequest();
        //设置用户信息
        if (user != null) {
            user.setPassword(null);
            cloudRequest.setUser(user);
        }
        if (plat != null) {
            cloudRequest.setPlat(plat);
        }
        //准备请求参数
        Map<String, Object> uriParams = new HashMap<>();
        uriParams.put("requestType", "1");
        //准备云方法请求体
        cloudRequest.setName(functionName);
        cloudRequest.setAppId(appId);
        cloudRequest.setParams(requestParams);
        cloudRequest.setBody(body);
        //将请求转发至业务服务器
        App app = appService.get(appId);
        if (app.getCloudSetting() == null || StringUtils.isEmpty(app.getCloudSetting().getCustomerHost())) {
            //未部署云代码或云代码地址为空
            throw new SimpleError(SimpleCode.CLOUD_NOT_DEPLOYED);
        } else {
            if (!app.getCloudSetting().hasFunction(functionName)) {
                //该方法未定义
                throw new SimpleError(SimpleCode.CLOUD_FUNCTION_NOT_FOUND);
            } else {
                //添加鉴权信息
                long timestamp = new Date().getTime();
                String timestampStr = String.valueOf(timestamp);
                cloudRequest.setTimestamp(timestampStr);
                //发送请求
                CloudResponse response;
                try {
                    response = rest.postForObject(app.getCloudSetting().getCustomerHost() + "?requestType={requestType}",
                            cloudRequest, CloudResponse.class, JBRequest.REQUEST_CLOUD);
                    if (response == null) {
                        throw new SimpleError(SimpleCode.CLOUD_FUNCTION_EXECUTE_FAILED);
                    }
                } catch (Exception e) {
                    //请求执行异常
                    throw new SimpleError(SimpleCode.CLOUD_FUNCTION_EXECUTE_FAILED);
                }
                if (response.getCode() == 0) {
                    //执行成功
                    SimpleResult simpleResult = SimpleResult.success();
                    simpleResult.setCode(response.getCode());
                    simpleResult.setMessage(response.getMessage());
                    if (response.getData() != null) {
                        simpleResult.putDataAll(response.getData());
                    }
                    return simpleResult;
                } else {
                    //执行失败
                    throw new SimpleError(response.getCode(), response.getMessage());
                }
            }
        }
    }

    public void deploy(String appId, CloudSetting setting) {
        appService.setCloudSetting(appId, setting);
    }

    public void unDeploy(String appId) {
        appService.setCloudSetting(appId, null);
    }
}
