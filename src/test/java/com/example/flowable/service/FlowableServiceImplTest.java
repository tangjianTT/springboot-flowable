package com.example.flowable.service;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
class FlowableServiceImplTest {

    @Autowired
    private FlowableServiceImpl flowableService;

    @Test
    void deploy() {
        flowableService.deploy("myLeave","myLeave.bpmn20.xml");
    }

    @Test
    void startProcessInstance() {
        Map<String,Object> map = new HashMap<>();
        // 设置人员
        map.put("zhuguan", Arrays.asList("a,b"));
        map.put("bumen", Arrays.asList("c","d"));
        map.put("caiwu",Arrays.asList("e","f"));
        map.put("dongshizhang",Arrays.asList("g","h"));
        flowableService.startProcessInstance("myLeave","001",map);
    }

    @Test
    void queryTaskByAssignee() {
        final List<Task> myLeave = flowableService.queryTaskByAssignee("myLeave", "%g%");
        myLeave.forEach(t-> System.out.printf(t.getId()+":"+t.getName()+":"+t.getProcessInstanceId()));
    }


    @Test
    void queryProInstanceStateByProInstanceId() {
        System.out.printf(flowableService.queryProInstanceStateByProInstanceId("70001").toString());
    }

    @Test
    void complateTask() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user_id","e");
        jsonObject.put("comment", "同意");
        final Task task = flowableService.getTaskByTaskId("75003");
        flowableService.complateTask(task,jsonObject);
    }

    @Test
    void queryHistory() {
        flowableService.queryHistory("70001");
    }

    @Test
    void rejectTaskList() {
        System.out.printf(flowableService.rejectTaskList("77503").toString());
    }

    @Test
    void rejectTask() {
        final Task task = flowableService.getTaskByTaskId("77503");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userId", "g");
        jsonObject.put("comment", "不同意");
        flowableService.rejectTask(task,"sid-2083229d-6872-4cd8-9e37-5589827ffddb",jsonObject);

    }

    @Test
    void delTask() {

    }




}