package com.isums.scheduleservice.configurations;

import com.isums.userservice.grpc.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientsConfig {

    @Bean
    UserServiceGrpc.UserServiceBlockingStub userStub(GrpcChannelFactory channels, GrpcTokenInterceptor tokenInterceptor) {
        return UserServiceGrpc.newBlockingStub(channels.createChannel("user"))
                .withInterceptors(tokenInterceptor);
    }
}
