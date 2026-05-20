package com.roddy.global.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisExampleService {

    private final StringRedisTemplate redisTemplate;

    public void saveData(String key, String value) {
        log.warn("saveData called without TTL for key={}", key);
        redisTemplate.opsForValue().set(key, value);
        log.debug("{} 키에 값을 저장했습니다. length={}", key, value == null ? 0 : value.length());
    }

    public void saveData(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
        log.debug("{} 키에 TTL과 함께 값을 저장했습니다. length={}, timeout={} {}", key, value == null ? 0 : value.length(), timeout, unit);
    }

    public String getData(String key) {
        String value = redisTemplate.opsForValue().get(key);
        log.debug("{} 키에서 값을 조회했습니다. found={}, length={}", key, value != null, value == null ? 0 : value.length());
        return value;
    }

    public void deleteData(String key) {
        redisTemplate.delete(key);
        log.info("{} 키를 삭제했습니다.", key);
    }
}
