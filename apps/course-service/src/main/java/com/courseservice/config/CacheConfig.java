package com.courseservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(ObjectMapper baseMapper) {
        // Copy Spring Boot's auto-configured ObjectMapper (has JavaTimeModule, etc.)
        // and add EVERYTHING default typing so records (which are final) get @class metadata.
        // Validator restricts deserialization to our app DTOs and JDK types only.
        ObjectMapper cacheMapper = baseMapper.copy()
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("com.courseservice")
                                .allowIfSubType("java.")
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY
                );

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "cache:" + cacheName + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper)));

        RedisCacheConfiguration analytics = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "cache:" + cacheName + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper)));

        return builder -> builder
                .withCacheConfiguration("courses", base)
                .withCacheConfiguration("courses:list", base)
                .withCacheConfiguration("analytics:instructor", analytics)
                .withCacheConfiguration("analytics:admin", analytics);
    }
}
