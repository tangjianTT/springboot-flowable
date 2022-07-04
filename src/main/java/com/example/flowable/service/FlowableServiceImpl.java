package com.example.flowable.service;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.util.ExecutionGraphUtil;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Tj
 * @description TODO
 * @date 2022/7/4 11:39
 **/
@Service
@Slf4j
public class FlowableServiceImpl {

    @Autowired
    protected RepositoryService repositoryService;
    @Autowired
    protected RuntimeService runtimeService;
    @Autowired
    protected TaskService taskService;
    @Autowired
    protected HistoryService historyService;
    @Autowired
    protected ManagementService managementService;

    @Autowired
    private ProcessEngine pe;

    public void deploy(String name,String fileName){
        log.info("====================================流程部署====================================");
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("processes/"+fileName)
                .addClasspathResource("processes/"+fileName)
                .name(name)
                .key(name)
                .deploy();
        log.info("部署流程成功：Id:{},Key:{},Name:{}",deployment.getId(),deployment.getKey(),deployment.getName());
    }

    public void startProcessInstance(String key,String businessKey,Map<String,Object> map) {
        log.info("====================================开启流程===================================="+key+":"+businessKey+":"+map);
        pe.getRuntimeService().startProcessInstanceByKey(key,businessKey,map);
    }

    public List<Task> queryTaskByAssignee(String key,String assignee){
        log.info("====================================获取个人任务===================================="+key+":"+assignee);
        return  taskService.createTaskQuery()
                .processDefinitionKey(key)
                .taskAssigneeLike(assignee)
                .orderByTaskCreateTime()
                .desc()
                .list();
    }

    public Task getTaskByTaskId(String taskId){
        log.info("====================================获取任务详情===================================="+taskId);
        return  taskService.createTaskQuery()
                .taskId(taskId)
                .orderByTaskCreateTime()
                .desc()
                .singleResult();
    }


    public Map<String,String> queryProInstanceStateByProInstanceId(String processInstanceId){
        log.info("====================================当前流程实例状态===================================="+processInstanceId);
        Map<String,String> map = new HashMap<>();
        final Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        if(task == null){
            map.put("code","0");
            map.put("data","当前流程已经完成");
            System.out.println("当前流程已经完成");
        }else{
            map.put("code","1");
            map.put("data","当前流程实例ID:"+task.getId()+"当前流程所处的位置："+task.getName());
            System.out.println("当前流程实例ID："+task.getId());
            System.out.println("当前流程所处的位置："+task.getName());
        }
        return map;
    }

    public void complateTask(Task task,JSONObject jsonObject) {
        log.info("====================================完成当前任务===================================="+task);
        taskService.addComment(task.getId(), task.getProcessInstanceId(), JSONObject.toJSONString(jsonObject));
        taskService.complete(task.getId());
    }

    public void queryHistory(String processInstanceId) {
        log.info("====================================任务历史记录===================================="+processInstanceId);
        // 单个流程历史
        HistoryService service=pe.getHistoryService();
        //获取Actinst表查询对象
        HistoricTaskInstanceQuery instanceQuery=service.createHistoricTaskInstanceQuery();
        instanceQuery.processInstanceId(processInstanceId);
        instanceQuery.orderByHistoricTaskInstanceEndTime().asc();
        List<HistoricTaskInstance> list=instanceQuery.list().stream().collect(Collectors.toList());
        //输出查询结果
        for(HistoricTaskInstance hi:list){
            System.out.println("------------------------");
            System.out.println(hi.getId());
            System.out.println(hi.getProcessInstanceId());
            System.out.println(hi.getName());
            System.out.println(hi.getAssignee());
            final List<Comment> taskComments = taskService.getTaskComments(hi.getId());
            if(taskComments.size() == 0){
                continue;
            }
            final Comment comment = taskComments.get(0);
            System.out.println(comment.getFullMessage());
            System.out.println(comment.getType());
            System.out.println(comment.getTime().toString());
            System.out.println("------------------------");
        }
    }

    public List<Map<String,String>> rejectTaskList(String taskId) {
        log.info("====================================可驳回列表===================================="+taskId);
        // 初始化返回结果列表
        List<Map<String,String>> result = new ArrayList<>(16);
        if (StringUtils.isBlank(taskId)){
            return result;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null){
            return result;
        }
        // 任务定义key 等于 当前任务节点id
        String taskDefinitionKey = task.getTaskDefinitionKey();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        Process mainProcess = bpmnModel.getMainProcess();
        // 当前节点
        FlowNode currentFlowElement = (FlowNode) mainProcess.getFlowElement(taskDefinitionKey, true);
        // 查询历史节点实例
        List<HistoricActivityInstance> activityInstanceList = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .finished()
                .orderByHistoricActivityInstanceEndTime().asc().list();
        List<String> activityIdList = activityInstanceList.stream()
                .filter(activityInstance ->
                        BpmnXMLConstants.ELEMENT_TASK_USER.equals(activityInstance.getActivityType()) || BpmnXMLConstants.ELEMENT_EVENT_START.equals(activityInstance.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .filter(activityId -> !taskDefinitionKey.equals(activityId))
                .distinct()
                .collect(Collectors.toList());
        for (String activityId : activityIdList) {
            // 回退到主流程的节点
            FlowNode toBackFlowElement = (FlowNode) mainProcess.getFlowElement(activityId, true);
            // 判断 【工具类判断是否可以从源节点 到 目标节点】
            Set<String> set = new HashSet<>();
            if (toBackFlowElement != null && StringUtils.isNotBlank(toBackFlowElement.getName()) && ExecutionGraphUtil.isReachable(mainProcess,toBackFlowElement,currentFlowElement, set)) {
                Map<String,String> map = new HashMap<>();
                map.put("nodeId",activityId);
                map.put("nodeName",toBackFlowElement.getName());

                result.add(map);
            }
        }
        return result;
    }

    public void rejectTask(Task task,String newActivityId,JSONObject jsonObject) {
        log.info("====================================驳回任务===================================="+task+":"+newActivityId);
        try {
            final Comment comment = taskService.addComment(task.getId(), task.getProcessInstanceId(), JSONObject.toJSONString(jsonObject));
            System.out.printf(comment.toString());
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdTo(task.getTaskDefinitionKey(), newActivityId)
                    .changeState();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void delTask(String processInstanceId) {
        if (StringUtils.isBlank(processInstanceId)) {
            return ;
        }
        try {
            //根据流程实例id 去ACT_RU_EXECUTION与ACT_RE_PROCDEF关联查询流程实例数据
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
            if (null != processInstance) {
                runtimeService.deleteProcessInstance(processInstanceId, "流程实例删除");
            } else {
                historyService.deleteHistoricProcessInstance(processInstanceId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
