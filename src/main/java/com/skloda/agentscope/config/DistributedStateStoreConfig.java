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
 * # application-postgresql.yml: spring.datasource.url/username/password
 * </pre>
 *
 * <h3>State Versioning (agentscope 2.0.3 — Optimistic Concurrency Control)</h3>
 * Since agentscope 2.0.3 the {@link AgentStateStore} interface ships opt-in OCC APIs:
 * {@code supportsVersioning()}, {@code getVersioned(userId, sessionId, key, type)}
 * (returns {@code VersionedState<T>} = value + monotonically increasing version) and
 * {@code saveIfVersion(userId, sessionId, key, state, expectedVersion)} where
 * {@code AgentStateStore.UNVERSIONED} (-1) is the "first write / no version" sentinel.
 * <p>
 * All three stores exposed here — {@link RedisAgentStateStore}, {@link MysqlAgentStateStore}
 * and {@link PostgresAgentStateStore} — implement these methods <b>natively and atomically</b>
 * in 2.0.3 ({@code supportsVersioning() == true}, confirmed against the extension jars):
 * <ul>
 *   <li>Redis: side-car version key ({@code RedisStateVersionSupport.versionKey}) checked and
 *       bumped by an embedded Lua CAS script executed as a single atomic EVAL;</li>
 *   <li>MySQL / PostgreSQL: version-conditional DML inside a write transaction
 *       ({@code executeInWriteTransaction});</li>
 *   <li>on version mismatch {@code saveIfVersion} returns -1, and the agent runtime
 *       (see {@code ReActAgent}, configurable via {@code ReActAgent.builder().conflictPolicy(...)}
 *       with {@code ConflictPolicy.OVERWRITE / FAIL / APPEND_MERGE}) surfaces it as
 *       {@code io.agentscope.core.state.ConcurrentSessionModificationException} when appropriate.</li>
 * </ul>
 * Versioning is therefore available out of the box on the {@code redis}/{@code mysql}/
 * {@code postgresql} profiles: <b>no extra property switch is required</b> (a decorator-based
 * opt-in toggle was deliberately not added because the extension stores already provide
 * first-class support; bean initialization logs the {@code supportsVersioning()} result above).
 * When no distributed profile is active, the default InMemory/JsonFile fallback stores are used
 * and default startup behavior is unchanged.
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
        AgentStateStore store = RedisAgentStateStore.builder()
                .jedisClient(jedis)
                .keyPrefix(keyPrefix)
                .build();
        log.info("S13: RedisAgentStateStore initialized (url={}, keyPrefix={}); state versioning (2.0.3 OCC): supported={}",
                redisUrl, keyPrefix, store.supportsVersioning());
        return store;
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
        AgentStateStore store = new MysqlAgentStateStore(dataSource);
        log.info("S13: MysqlAgentStateStore initialized; state versioning (2.0.3 OCC): supported={}",
                store.supportsVersioning());
        return store;
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
        AgentStateStore store = new PostgresAgentStateStore(dataSource);
        log.info("S13: PostgresAgentStateStore initialized; state versioning (2.0.3 OCC): supported={}",
                store.supportsVersioning());
        return store;
    }
}
