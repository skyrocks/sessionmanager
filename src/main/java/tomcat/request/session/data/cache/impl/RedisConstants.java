package tomcat.request.session.data.cache.impl;

public interface RedisConstants {
    String PROPERTIES_FILE = "redis-data-cache.properties";
    String HOSTS = "redis.hosts";
    String CLUSTER_ENABLED = "redis.cluster.enabled";
    String MAX_ACTIVE = "redis.max.active";
    String TEST_ONBORROW = "redis.test.onBorrow";
    String TEST_ONRETURN = "redis.test.onReturn";
    String MAX_IDLE = "redis.max.idle";
    String MIN_IDLE = "redis.min.idle";
    String TEST_WHILEIDLE = "redis.test.whileIdle";
    String TEST_NUMPEREVICTION = "redis.test.numPerEviction";
    String TIME_BETWEENEVICTION = "redis.time.betweenEviction";
    String PASSWORD = "redis.password";
    String DATABASE = "redis.database";
    String TIMEOUT = "redis.timeout";
    String DEFAULT_MAX_ACTIVE_VALUE = "10";
    String DEFAULT_TEST_ONBORROW_VALUE = "true";
    String DEFAULT_TEST_ONRETURN_VALUE = "true";
    String DEFAULT_MAX_IDLE_VALUE = "5";
    String DEFAULT_MIN_IDLE_VALUE = "1";
    String DEFAULT_TEST_WHILEIDLE_VALUE = "true";
    String DEFAULT_TEST_NUMPEREVICTION_VALUE = "10";
    String DEFAULT_TIME_BETWEENEVICTION_VALUE = "60000";
    String DEFAULT_CLUSTER_ENABLED = "false";
    String CONN_FAILED_RETRY_MSG = "Jedis connection failed, retrying...";
}
