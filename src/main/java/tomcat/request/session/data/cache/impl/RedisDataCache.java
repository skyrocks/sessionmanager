package tomcat.request.session.data.cache.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisClusterMaxRedirectionsException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import tomcat.request.session.data.cache.DataCache;

import java.io.*;
import java.util.*;

public class RedisDataCache implements DataCache {
    private static DataCache dataCache;
    private Log log = LogFactory.getLog(RedisDataCache.class);

    public RedisDataCache() {
        this.initialize();
    }

    public byte[] set(String key, byte[] value) {
        return dataCache.set(key, value);
    }

    public Long setnx(String key, byte[] value) {
        return dataCache.setnx(key, value);
    }

    public Long expire(String key, int seconds) {
        return dataCache.expire(key, seconds);
    }

    public byte[] get(String key) {
        return key != null ? dataCache.get(key) : null;
    }

    public Long delete(String key) {
        return dataCache.delete(key);
    }

    public static String parseDataCacheKey(String key) {
        return key.replaceAll("\\s", "_");
    }

    private void initialize() {
        if (dataCache == null) {
            Properties properties = this.loadProperties();
            boolean clusterEnabled = Boolean.valueOf(properties.getProperty("redis.cluster.enabled", "false"));
            String hosts = properties.getProperty("redis.hosts", "localhost".concat(":").concat(String.valueOf(6379)));
            Collection<? extends Serializable> nodes = this.getJedisNodes(hosts, clusterEnabled);
            this.log.info("redis.hosts " + hosts);
            String password = properties.getProperty("redis.password");
            password = password != null && !password.isEmpty() ? password : null;
            int database = Integer.parseInt(properties.getProperty("redis.database", String.valueOf(0)));
            int timeout = Integer.parseInt(properties.getProperty("redis.timeout", String.valueOf(2000)));
            this.log.info("redis.timeout a " + timeout);
            timeout = timeout < 2000 ? 2000 : timeout;
            this.log.info("redis.timeout b " + timeout);
            if (clusterEnabled) {
                dataCache = new RedisDataCache.RedisClusterCacheUtil((Set)nodes, password, timeout, this.getPoolConfig(properties));
            } else {
                dataCache = new RedisDataCache.RedisCacheUtil((String)((List)nodes).get(0), Integer.parseInt((String)((List)nodes).get(1)), password, database, timeout, this.getPoolConfig(properties));
            }

        }
    }

    private JedisPoolConfig getPoolConfig(Properties properties) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        int maxActive = Integer.parseInt(properties.getProperty("redis.max.active", "10"));
        poolConfig.setMaxTotal(maxActive);
        boolean testOnBorrow = Boolean.parseBoolean(properties.getProperty("redis.test.onBorrow", "true"));
        poolConfig.setTestOnBorrow(testOnBorrow);
        boolean testOnReturn = Boolean.parseBoolean(properties.getProperty("redis.test.onReturn", "true"));
        poolConfig.setTestOnReturn(testOnReturn);
        int maxIdle = Integer.parseInt(properties.getProperty("redis.max.active", "10"));
        poolConfig.setMaxIdle(maxIdle);
        int minIdle = Integer.parseInt(properties.getProperty("redis.min.idle", "1"));
        poolConfig.setMinIdle(minIdle);
        boolean testWhileIdle = Boolean.parseBoolean(properties.getProperty("redis.test.whileIdle", "true"));
        poolConfig.setTestWhileIdle(testWhileIdle);
        int testNumPerEviction = Integer.parseInt(properties.getProperty("redis.test.numPerEviction", "10"));
        poolConfig.setNumTestsPerEvictionRun(testNumPerEviction);
        long timeBetweenEviction = Long.parseLong(properties.getProperty("redis.time.betweenEviction", "60000"));
        poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEviction);
        return poolConfig;
    }

    private Collection<? extends Serializable> getJedisNodes(String hosts, boolean clusterEnabled) {
        hosts = hosts.replaceAll("\\s", "");
        String[] hostPorts = hosts.split(",");
        List<String> node = null;
        Set<HostAndPort> nodes = null;
        String[] var6 = hostPorts;
        int var7 = hostPorts.length;

        for(int var8 = 0; var8 < var7; ++var8) {
            String hostPort = var6[var8];
            String[] hostPortArr = hostPort.split(":");
            if (clusterEnabled) {
                nodes = nodes == null ? new HashSet() : nodes;
                nodes.add(new HostAndPort(hostPortArr[0], Integer.valueOf(hostPortArr[1])));
            } else {
                int port = Integer.valueOf(hostPortArr[1]);
                if (!hostPortArr[0].isEmpty() && port > 0) {
                    node = node == null ? new ArrayList() : node;
                    node.add(hostPortArr[0]);
                    node.add(String.valueOf(port));
                    break;
                }
            }
        }

        return (Collection)(clusterEnabled ? nodes : node);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();

        try {
            String filePath = System.getProperty("catalina.base").concat(File.separator).concat("conf").concat(File.separator).concat("redis-data-cache.properties");
            Object resourceStream = null;

            try {
                resourceStream = filePath != null && !filePath.isEmpty() && (new File(filePath)).exists() ? new FileInputStream(filePath) : null;
                if (resourceStream == null) {
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    resourceStream = loader.getResourceAsStream("redis-data-cache.properties");
                }

                properties.load((InputStream)resourceStream);
            } finally {
                ((InputStream)resourceStream).close();
            }
        } catch (IOException var9) {
            this.log.error("Error while loading task scheduler properties", var9);
        }

        return properties;
    }

    private class RedisClusterCacheUtil implements DataCache {
        private JedisCluster cluster;
        private static final int NUM_RETRIES = 30;
        private static final int DEFAULT_MAX_REDIRECTIONS = 5;
        private Log log = LogFactory.getLog(RedisDataCache.RedisClusterCacheUtil.class);

        public RedisClusterCacheUtil(Set<HostAndPort> nodes, String password, int timeout, JedisPoolConfig poolConfig) {
            this.cluster = new JedisCluster(nodes, timeout, 2000, 5, password, poolConfig);
        }

        public byte[] set(String key, byte[] value) {
            int tries = 0;
            boolean sucess = false;
            String retVal = null;

            do {
                ++tries;

                try {
                    retVal = this.cluster.set(key.getBytes(), value);
                    sucess = true;
                } catch (JedisConnectionException | JedisClusterMaxRedirectionsException var7) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 30) {
                        throw var7;
                    }

                    this.waitforFailover();
                }
            } while(!sucess && tries <= 30);

            return retVal != null ? retVal.getBytes() : null;
        }

        public Long setnx(String key, byte[] value) {
            int tries = 0;
            boolean sucess = false;
            Long retVal = null;

            do {
                ++tries;

                try {
                    retVal = this.cluster.setnx(key.getBytes(), value);
                    sucess = true;
                } catch (JedisConnectionException | JedisClusterMaxRedirectionsException var7) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 30) {
                        throw var7;
                    }

                    this.waitforFailover();
                }
            } while(!sucess && tries <= 30);

            return retVal;
        }

        public Long expire(String key, int seconds) {
            int tries = 0;
            boolean sucess = false;
            Long retVal = null;

            do {
                ++tries;

                try {
                    retVal = this.cluster.expire(key, seconds);
                    sucess = true;
                } catch (JedisConnectionException | JedisClusterMaxRedirectionsException var7) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 30) {
                        throw var7;
                    }

                    this.waitforFailover();
                }
            } while(!sucess && tries <= 30);

            return retVal;
        }

        public byte[] get(String key) {
            int tries = 0;
            boolean sucess = false;
            byte[] retVal = null;

            do {
                ++tries;

                try {
                    retVal = this.cluster.get(key.getBytes());
                    sucess = true;
                } catch (JedisConnectionException | JedisClusterMaxRedirectionsException var6) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 30) {
                        throw var6;
                    }

                    this.waitforFailover();
                }
            } while(!sucess && tries <= 30);

            return retVal;
        }

        public Long delete(String key) {
            int tries = 0;
            boolean sucess = false;
            Long retVal = null;

            do {
                ++tries;

                try {
                    retVal = this.cluster.del(key);
                    sucess = true;
                } catch (JedisConnectionException | JedisClusterMaxRedirectionsException var6) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 30) {
                        throw var6;
                    }

                    this.waitforFailover();
                }
            } while(!sucess && tries <= 30);

            return retVal;
        }

        private void waitforFailover() {
            try {
                Thread.sleep(4000L);
            } catch (InterruptedException var2) {
                Thread.currentThread().interrupt();
            }

        }
    }

    private class RedisCacheUtil implements DataCache {
        private JedisPool pool;
        private static final int NUM_RETRIES = 3;
        private Log log = LogFactory.getLog(RedisDataCache.RedisCacheUtil.class);

        public RedisCacheUtil(String host, int port, String password, int database, int timeout, JedisPoolConfig poolConfig) {
            this.pool = new JedisPool(poolConfig, host, port, timeout, password, database);
        }

        public byte[] set(String key, byte[] value) {
            int tries = 0;
            boolean sucess = false;
            String retVal = null;

            do {
                ++tries;

                try {
                    Jedis jedis = this.pool.getResource();
                    retVal = jedis.set(key.getBytes(), value);
                    jedis.close();
                    sucess = true;
                    this.log.info("set success!!!!!!!!!!!!!!");
                } catch (JedisConnectionException var7) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 3) {
                        throw var7;
                    }
                }
            } while(!sucess && tries <= 3);

            if (retVal != null) {
                this.log.info("set success, return retVal !!!!!!!!!!!!!!");
                return retVal.getBytes();
            } else {
                return null;
            }
        }

        public Long setnx(String key, byte[] value) {
            int tries = 0;
            boolean sucess = false;
            Long retVal = null;

            do {
                ++tries;

                try {
                    Jedis jedis = this.pool.getResource();
                    retVal = jedis.setnx(key.getBytes(), value);
                    jedis.close();
                    sucess = true;
                } catch (JedisConnectionException var7) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 3) {
                        throw var7;
                    }
                }
            } while(!sucess && tries <= 3);

            return retVal;
        }

        public Long expire(String key, int seconds) {
            int tries = 0;
            boolean sucess = false;
            Long retVal = null;

            do {
                ++tries;

                try {
                    Jedis jedis = this.pool.getResource();
                    retVal = jedis.expire(key, seconds);
                    jedis.close();
                    sucess = true;
                } catch (JedisConnectionException var7) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 3) {
                        throw var7;
                    }
                }
            } while(!sucess && tries <= 3);

            return retVal;
        }

        public byte[] get(String key) {
            int tries = 0;
            boolean sucess = false;
            byte[] retVal = null;

            do {
                ++tries;

                try {
                    this.log.info("get key" + key);
                    Jedis jedis = this.pool.getResource();
                    retVal = jedis.get(key.getBytes());
                    this.log.info("get key success !!!!!!!!!" + key);
                    jedis.close();
                    sucess = true;
                } catch (JedisConnectionException var6) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 3) {
                        throw var6;
                    }
                }
            } while(!sucess && tries <= 3);

            return retVal;
        }

        public Long delete(String key) {
            int tries = 0;
            boolean sucess = false;
            Long retVal = null;

            do {
                ++tries;

                try {
                    Jedis jedis = this.pool.getResource();
                    retVal = jedis.del(key);
                    jedis.close();
                    sucess = true;
                } catch (JedisConnectionException var6) {
                    this.log.error("Jedis connection failed, retrying..." + tries);
                    if (tries == 3) {
                        throw var6;
                    }
                }
            } while(!sucess && tries <= 3);

            return retVal;
        }
    }
}
