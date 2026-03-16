//package com.isums.scheduleservice.configurations;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.CorsConfigurationSource;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//
//import java.util.List;
//
//@Configuration
//public class CorsConfig {
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration config = new CorsConfiguration();
//
//        // DÙNG PATTERNS cho ngrok (vì domain thay đổi) + cho cả .dev và .app
//        config.setAllowedOriginPatterns(List.of(
//                "https://*.ngrok-free.dev",
//                "https://*.ngrok-free.app",
//                "http://localhost:3000"      // nếu test web local
//        ));
//
//        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
//        config.setAllowedHeaders(List.of("*"));
//        // Nếu bạn cần đọc Set-Cookie… từ web:
//        config.setExposedHeaders(List.of("Location")); // thêm gì tuỳ nhu cầu
//        config.setAllowCredentials(true);
//        config.setMaxAge(3600L);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//        return source;
//    }
//}
