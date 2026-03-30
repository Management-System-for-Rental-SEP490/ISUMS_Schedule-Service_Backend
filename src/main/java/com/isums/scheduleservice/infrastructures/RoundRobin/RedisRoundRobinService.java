package com.isums.scheduleservice.infrastructures.RoundRobin;

import com.isums.scheduleservice.domains.dtos.StaffScore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RedisRoundRobinService {
    private final StringRedisTemplate redisTemplate;

    public int getNextIndex(UUID regionId, int size) {
        String key = "rr:region:" + regionId;

        Long index = redisTemplate.opsForValue().increment(key);

        if (index == null) {
            throw new RuntimeException("Redis increment failed");
        }

        return (int) (index % size);
    }
}
