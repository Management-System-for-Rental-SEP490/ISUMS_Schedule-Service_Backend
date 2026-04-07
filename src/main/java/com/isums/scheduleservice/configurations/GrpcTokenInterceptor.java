//package com.isums.scheduleservice.configurations;
//
//import io.grpc.*;
//import lombok.RequiredArgsConstructor;
//
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class GrpcTokenInterceptor implements ClientInterceptor {
//
//    private final KeycloakTokenService tokenService;
//
//    private static final Metadata.Key<String> AUTHORIZATION_KEY =
//            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
//
//    @Override
//    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
//            MethodDescriptor<ReqT, RespT> method,
//            CallOptions callOptions,
//            Channel next) {
//
//        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
//            @Override
//            public void start(Listener<RespT> responseListener, Metadata headers) {
//
//                String token = tokenService.getToken();
//
//                headers.put(AUTHORIZATION_KEY, "Bearer " + token);
//                System.out.println("TOKEN = " + token);
//                super.start(responseListener, headers);
//            }
//        };
//    }
//}
