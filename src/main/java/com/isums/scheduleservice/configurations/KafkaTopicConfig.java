package com.isums.scheduleservice.configurations;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    private static final int JOB_TOPIC_PARTITIONS = 3;

    @Bean
    public NewTopic jobCreatedTopic() {
        return jobTopic("job.created");
    }

    @Bean
    public NewTopic jobScheduledTopic() {
        return jobTopic("job.scheduled");
    }

    @Bean
    public NewTopic jobRescheduledTopic() {
        return jobTopic("job.rescheduled");
    }

    @Bean
    public NewTopic jobNeedRescheduleTopic() {
        return jobTopic("job.need-reschedule");
    }

    @Bean
    public NewTopic jobAssignedTopic() {
        return jobTopic("job.assigned");
    }

    @Bean
    public NewTopic jobWaitingConfirmTopic() {
        return jobTopic("job.waiting.confirm");
    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return jobTopic("job.completed");
    }

    private NewTopic jobTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(JOB_TOPIC_PARTITIONS)
                .replicas(1)
                .build();
    }
}
