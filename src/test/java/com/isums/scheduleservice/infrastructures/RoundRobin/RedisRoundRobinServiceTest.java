package com.isums.scheduleservice.infrastructures.RoundRobin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRoundRobinService")
class RedisRoundRobinServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private RedisRoundRobinService service;

    private UUID regionId;

    @BeforeEach
    void setUp() {
        regionId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("returns (incremented % size) with region-scoped key")
    void increments() {
        when(valueOps.increment("rr:region:" + regionId)).thenReturn(7L);

        int idx = service.getNextIndex(regionId, 3);

        assertThat(idx).isEqualTo(1); // 7 % 3
        verify(valueOps).increment("rr:region:" + regionId);
    }

    @Test
    @DisplayName("wraps around correctly on multiple increments")
    void wrapsAround() {
        when(valueOps.increment("rr:region:" + regionId)).thenReturn(4L);

        assertThat(service.getNextIndex(regionId, 2)).isEqualTo(0); // 4 % 2
    }

    @Test
    @DisplayName("throws when Redis increment returns null")
    void nullIncrementThrows() {
        when(valueOps.increment("rr:region:" + regionId)).thenReturn(null);

        assertThatThrownBy(() -> service.getNextIndex(regionId, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis increment failed");
    }
}
