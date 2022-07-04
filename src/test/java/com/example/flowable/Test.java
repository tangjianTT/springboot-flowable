package com.example.flowable;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.constants.BpmnXMLConstants;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.util.ExecutionGraphUtil;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Tj
 * @description TODO
 * @date 2022/6/30 15:54
 **/
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class Test {

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

    //流程部署
    @org.junit.Test
    // 影响表：act_ge_bytearray(通用流程定义和流程资源表)    act_re_deployment(部署单元信息表)   act_re_procdef(已部署的流程定义表)
    public void deploymentDefinition(){
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("processes/myLeave.bpmn20.xml")
                .addClasspathResource("processes/myLeave.bpmn20.png")
                .name("diagram")
                .deploy();
        log.info("部署流程成功：Id:{},Key:{},Name:{}",deployment.getId(),deployment.getKey(),deployment.getName());
    }


    @org.junit.Test
    public void queryProcessDefinition() {
        // 部署查询对象----查询部署表act_re_deployment，也就是流程列表。。
        ProcessDefinitionQuery query = pe.getRepositoryService().createProcessDefinitionQuery();
        query.latestVersion();
        List<ProcessDefinition> list = query.list();
        for (ProcessDefinition processDefinition : list) {
            System.out.println(processDefinition.getId() + "-------" + processDefinition.getName());
        }
    }

    @org.junit.Test
    public void startProcessInstance() {
        // 影响表：act_hi_actinst(历史流程实例)    act_hi_detail(历史的流程运行中的细节)   act_hi_identitylink(历史的流程运行过程中用户关系)
        //       act_hi_procinst(历史流程实例)   act_hi_taskinst(历史任务实例)                act_hi_varinst (历史的流程运行中的变量信息)
        //       act_ru_execution(运行时流程执行实例)                 act_ru_task(运行时任务)
        // 开启流程
        Map<String,Object> map = new HashMap<>();
        // 设置人员
        map.put("zhuguan", Arrays.asList("a,b"));
        map.put("bumen", Arrays.asList("c","d"));
        map.put("caiwu",Arrays.asList("e","f"));
        map.put("dongshizhang",Arrays.asList("g","h"));
        ProcessInstance pi = pe.getRuntimeService().startProcessInstanceByKey("myLeave","2",map);
        System.out.println(pi.getBusinessKey());
    }


    /**
     * 根据办理人查询任务
     */
    @org.junit.Test
    public void queryTaskByAssignee(){
        String assignee = "%a%";
        List<Task> taskList = taskService.createTaskQuery()
                .processDefinitionKey("myLeave")
                .taskAssigneeLike(assignee)
                .orderByTaskCreateTime()
                .desc()
                .list();
         for(Task task: taskList){
            System.out.println("任务id: "+task.getId());
            System.out.println("任务名字: "+task.getName());
            System.out.println("任务创建时间: "+task.getCreateTime());
            System.out.println("办理人: "+task.getAssignee());
        }
    }


    /**
     * 查询当前流程实例状态
     */
    @org.junit.Test
    public void queryProInstanceStateByProInstanceId(){
        final Task task = taskService.createTaskQuery().processInstanceId("30001").singleResult();
        if(task == null){
            System.out.println("当前流程已经完成");
        }else{
            System.out.println("当前流程实例ID："+task.getId());
            System.out.println("当前流程所处的位置："+task.getName());
        }
    }




    @org.junit.Test
    public void complateTask() {
        // 流程正常审批
        String ac = "%f%";
        // 查询所有任务
        List<Task> tasks = taskService.createTaskQuery().taskAssigneeLike(ac).list();
        for (Task task : tasks) {
            System.out.println("ID:"+task.getId()+",姓名:"+task.getName()+",接收人:"+task.getAssignee()+",开始时间:"+task.getCreateTime());
        }
        final Task task = tasks.get(0);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userId", "f");
        jsonObject.put("comment", "同意");
        taskService.addComment(task.getId(), task.getProcessInstanceId(), JSONObject.toJSONString(jsonObject));
        taskService.complete(task.getId());
    }



    @org.junit.Test
    public void wtaskHistory() {
        // 单个流程历史
        HistoryService service=pe.getHistoryService();
        //获取Actinst表查询对象
        HistoricTaskInstanceQuery instanceQuery=service.createHistoricTaskInstanceQuery();
        instanceQuery.processInstanceId("52501");
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

    @org.junit.Test
    public void rejectTaskList() {
        // 获取传入的当前任务id
        String taskId = "65004";
        // 初始化返回结果列表
        List<Map<String,String>> result = new ArrayList<>(16);
        if (StringUtils.isBlank(taskId)){
            return ;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null){
            return ;
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
            if (toBackFlowElement != null && ExecutionGraphUtil.isReachable(mainProcess,toBackFlowElement,currentFlowElement, set)) {
               Map<String,String> map = new HashMap<>();
                map.put("nodeId",activityId);
                map.put("nodeName",toBackFlowElement.getName());

                result.add(map);
            }
        }
        System.out.println(result.toString());
    }

    @org.junit.Test
    public void rejectTask() {
        String taskId = "60003";
        if (StringUtils.isBlank(taskId)){
            return ;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null){
            return ;
        }
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId", "g");
            jsonObject.put("comment", "不同意");
            final Comment comment = taskService.addComment(task.getId(), task.getProcessInstanceId(), JSONObject.toJSONString(jsonObject));
            System.out.printf(comment.toString());
            // 当前节点
            String taskDefinitionKey = task.getTaskDefinitionKey();
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId("52501")
                    .moveActivityIdTo(taskDefinitionKey, "sid-5b28e5f6-2efa-43c7-81d4-33d57772406d")
                    .changeState();
            System.out.printf("123");
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    @org.junit.Test
    public void delTask() {
        if (StringUtils.isBlank("5001")) {
            return ;
        }
        try {
            //根据流程实例id 去ACT_RU_EXECUTION与ACT_RE_PROCDEF关联查询流程实例数据
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId("5001").singleResult();
            if (null != processInstance) {
                runtimeService.deleteProcessInstance("5001", "流程实例删除");
            } else {
                historyService.deleteHistoricProcessInstance("5001");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public void suspendAllProcessInstance() {
        // 1、获取流程引擎
        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();

        // 2、获取Repositoryservice
        RepositoryService repositoryService = processEngine.getRepositoryService();

        // 3、查询流程定义,获取流程定义的查询对象
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("myLeave").singleResult();

        //4、获取当前流程定义的实例是否都是挂起状态
        boolean suspended = processDefinition.isSuspended();

        //5、获取流程定义的id
        String definitionId = processDefinition.getId();

        //6、如果是挂起状态，改为激活状态
        if (suspended) {

            //如果是挂起，可以执行激活的操作,参数1:流程定义id参数2: 是否激活，参数3: 激活时间
            repositoryService.activateProcessDefinitionById(definitionId,
                    true,
                    null);
            System.out.println("流程定义id:" + definitionId + ",已激活");
        } else {

            // 7、如果是激活状态，改为挂起状态,参数1:流程定义id参数2: 是否暂停参数了:暂停的时间
            repositoryService.suspendProcessDefinitionById(definitionId,
                    true,
                    null);
            System.out.println("流程定义id:" + definitionId + ",已挂起");
        }
    }


}
