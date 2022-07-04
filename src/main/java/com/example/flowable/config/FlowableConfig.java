package com.example.flowable.config;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {
    @Override
    public void configure(SpringProcessEngineConfiguration engineConfiguration) {
        engineConfiguration.setActivityFontName("宋体");
        engineConfiguration.setLabelFontName("宋体");
        engineConfiguration.setAnnotationFontName("宋体");
        engineConfiguration.setAsyncExecutorActivate(true);//开启异步
        //事件监听器
       /* List<FlowableEventListener> listenerList=new ArrayList<>();
        listenerList.add(new MyListener());
        engineConfiguration.setEventListeners(listenerList);*/
    }

    @Bean
    public BpmnXMLConverter bpmnXmlConverter(){
        return new BpmnXMLConverter();
    }

}
