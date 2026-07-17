package com.skloda.agentscope.config;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.clients.jedis.JedisPooled;

import javax.sql.DataSource;

/**
 * S13: Distributed AgentStateStore configuration.
 * <p>
 * Activated via Spring profile: {@code --spring.profiles.active=redis|mysql|postgresql}.
 * When active, exposes an {@link AgentStateStore} bean that {@code AgentFactory.createStateStore}
 * will pick up (via {@code @Autowired(required=false)}) in preference to the default InMemory store.
 * <p>
 * When no distributed profile is active, no bean is created and the app falls back to
 * {@code InMemoryAgentStateStore}/{@code JsonFileAgentStateStore} as before (zero impact on default startup).
 *
 * <h3>Usage</h3>
 * <pre>
 * # Redis (simplest, no schema setup)
 * --spring.profiles.active=redis
 * # application-redis.yml: agentscope.distributed.redis.url=redis://localhost:6379
 *
 * # MySQL
 * --spring.profiles.active=mysql
 * # application-mysql.yml: spring.datasource.url/h.username/password
 *
 * # PostgreSQL
 * --spring.profiles.active=postgresql
 * # application-postgresql.yml: spring.datasource.url/h.username/password
 * </pre>
 */
@Configuration
@Profile({"redis", "mysql", "postgresql"})
public class DistributedStateStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(DistributedStateStoreConfig.class);

    /**
     * Redis-backed state store. Requires {@code agentscope.distributed.redis.url}
     * (default {@code redis://localhost:6379}).
     */
    @Bean
    @Profile("redis")
    public AgentStateStore redisStateStore(
            @Value("${agentscope.distributed.redis.url:redis://localhost:6379}") String redisUrl,
            @Value("${agentscope.distributed.redis.key-prefix:agentscope}") String keyPrefix) {
        JedisPooled jedis = new JedisPooled(redisUrl);
        log.info("S13: RedisAgentStateStore initialized (url={}, keyPrefix={})", redisUrl, keyPrefix);
        return RedisAgentStateStore.builder()
                .jedisClient(jedis)
                .keyPrefix(keyPrefix)
                .build();
    }

    /**
     * MySQL-backed state store. DataSource is built from {@code spring.datasource.*}
     * in {@code application-mysql.yml} (DataSource auto-config is excluded globally
     * so the default profile needs no DB).
     */
    @Bean
    @Profile("mysql")
    public DataSource mysqlDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driver) {
        DataSource ds = DataSourceBuilder.create()
                .url(url).username(username).password(password)
                .driverClassName(driver)
                .build();
        log.info("S13: MySQL DataSource initialized (url={})", url);
        return ds;
    }

    @Bean
    @Profile("mysql")
    public AgentStateStore mysqlStateStore(DataSource dataSource) {
        log.info("S13: MysqlAgentStateStore initialized");
        return new MysqlAgentStateStore(dataSource);
    }

    /**
     * PostgreSQL-backed state store. DataSource is built from {@code spring.datasource.*}
     * in {@code application-postgresql.yml}.
     */
    @Bean
    @Profile("postgresql")
    public DataSource postgresqlDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}") String driver) {
        DataSource ds = DataSourceBuilder.create()
                .url(url).username(username).password(password)
                .driverClassName(driver)
                .build();
        log.info("S13: PostgreSQL DataSource initialized (url={})", url);
        return ds;
    }

    @Bean
    @Profile("postgresql")
    public AgentStateStore postgresStateStore(DataSource dataSource) {
        log.info("S13: PostgresAgentStateStore initialized");
        return new PostgresAgentStateStore(dataSource);
    }
}
