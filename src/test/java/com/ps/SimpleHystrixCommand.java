package com.ps;

import com.netflix.hystrix.HystrixCommand;

import java.util.concurrent.Callable;

class SimpleHystrixCommand<T> extends HystrixCommand<T> {

    private final Callable<T> callable;
    private final Callable<T> fallback;

    public SimpleHystrixCommand(Setter setter, Callable<T> callable) {
        this(setter, callable, null);
    }

    public SimpleHystrixCommand(Setter setter, Callable<T> callable, Callable<T> fallback) {
        super(setter);
        this.callable = callable;
        this.fallback = fallback;
    }

    @Override
    protected T run() throws Exception {
        return callable.call();
    }

    @Override
    protected T getFallback() {
        if (fallback == null)
            return super.getFallback();
        else
            try {
                return fallback.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }
}
