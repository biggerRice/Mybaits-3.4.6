package com.dm.plugin;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;

import java.util.Properties;

/**
 * @author wind
 */
public class ExamplePlugin implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("ExamplePlugin -> intercept");
        return invocation;
    }

    @Override
    public Object plugin(Object target) {
        System.out.println("ExamplePlugin -> plugin");
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        System.out.println("ExamplePlugin -> setProperties");
    }
}
