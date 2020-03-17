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
    private GenericObjectPool<NodeJs> pool;

    public NodeJsExecutor(String script) {
        LOGGER.config(String.format("Initializing factory for nodeJs pool objects (%s)", BasePooledObjectFactory.class.getName()));
        PooledObjectFactory<NodeJs> pooledObjectFactory = initPooledObjectFactory(script);
        LOGGER.config(String.format("Initializing config for nodeJs pool (%s)", GenericObjectPoolConfig.class.getName()));
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
        return new GenericObjectPoolConfig<>() {{
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            setMaxTotal(availableProcessors);
            setMaxIdle(availableProcessors);
            setMinEvictableIdleTimeMillis(Duration.ofMinutes(30).toMillis());
            setTimeBetweenEvictionRunsMillis(Duration.ofMinutes(5).toMillis());
        }};
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
