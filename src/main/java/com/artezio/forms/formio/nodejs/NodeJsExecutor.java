package com.artezio.forms.formio.nodejs;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.logging.Logger;

public class NodeJsExecutor {

    private static final Logger LOGGER = Logger.getLogger(NodeJsExecutor.class.getName());
    private static final int AVAILABLE_PROCESSORS_NUMBER = Runtime.getRuntime().availableProcessors();
    
    private static final int NODEJS_POOL_MAX_TOTAL = Integer
            .parseInt(System.getProperty("NODEJS_POOL_MAX_TOTAL", "" + AVAILABLE_PROCESSORS_NUMBER));
    
    private static final int NODEJS_POOL_MAX_IDLE = Integer
            .parseInt(System.getProperty("NODEJS_POOL_MAX_IDLE", "" + AVAILABLE_PROCESSORS_NUMBER));
    
    private static final long NODEJS_POOL_MIN_EVICTABLE_IDLE_TIME_MINS = Long
            .parseLong(System.getProperty("NODEJS_POOL_MIN_EVICTABLE_IDLE_TIME_MINS", "30"));
    
    private static final long NODEJS_POOL_TIME_BETWEEN_EVICTION_RUNS_MINS = Long
            .parseLong(System.getProperty("NODEJS_POOL_TIME_BETWEEN_EVICTION_RUNS_MINS", "5"));
    
    private GenericObjectPool<NodeJs> pool;

    public NodeJsExecutor(String script) {
        LOGGER.config(String.format("Initializing factory for nodeJs pool objects (%s)",
                BasePooledObjectFactory.class.getName()));
        PooledObjectFactory<NodeJs> pooledObjectFactory = initPooledObjectFactory(script);
        LOGGER.config(
                String.format("Initializing config for nodeJs pool (%s)", GenericObjectPoolConfig.class.getName()));
        GenericObjectPoolConfig<NodeJs> poolConfig = initPoolConfig();
        LOGGER.config("Creating nodeJs pool");
        pool = new GenericObjectPool<>(pooledObjectFactory, poolConfig);
    }

    private BasePooledObjectFactory<NodeJs> initPooledObjectFactory(String script) {
        return new BasePooledObjectFactory<>() {

            @Override
            public NodeJs create() {
                return new NodeJs(script);
            }

            @Override
            public PooledObject<NodeJs> wrap(NodeJs obj) {
                return new DefaultPooledObject<>(obj);
            }

            @Override
            public void destroyObject(PooledObject<NodeJs> pooledObject) throws InterruptedException {
                pooledObject.getObject().shutdown();
            }

        };
    }

    private GenericObjectPoolConfig<NodeJs> initPoolConfig() {
        return new GenericObjectPoolConfig<>() {
            {
                setMaxTotal(NODEJS_POOL_MAX_TOTAL);
                setMaxIdle(NODEJS_POOL_MAX_IDLE);
                setMinEvictableIdleTimeMillis(Duration.ofMinutes(NODEJS_POOL_MIN_EVICTABLE_IDLE_TIME_MINS).toMillis());
                setTimeBetweenEvictionRunsMillis(Duration.ofMinutes(NODEJS_POOL_TIME_BETWEEN_EVICTION_RUNS_MINS).toMillis());
            }
        };
    }

    public String execute(String arguments) throws Exception {
        NodeJs nodeJs = null;
        try {
            nodeJs = pool.borrowObject();
            return nodeJs.execute(arguments);
        } finally {
            pool.returnObject(nodeJs);
        }
    }

}
