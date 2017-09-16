package com.ps;

import com.netflix.hystrix.HystrixObservableCommand;
import rx.Observable;

class SimpleObservableHystrixCommand<T> extends HystrixObservableCommand<T> {

    private final Observable<T> observable;
    private final Observable<T> fallback;

    public SimpleObservableHystrixCommand(Setter setter, Observable<T> observable) {
        this(setter, observable, null);
    }

    public SimpleObservableHystrixCommand(Setter setter, Observable<T> observable, Observable<T> fallback) {
        super(setter);
        this.observable = observable;
        this.fallback = fallback;
    }

    @Override
    protected Observable<T> construct() {
        return observable;
    }

    @Override
    protected Observable<T> resumeWithFallback() {
        return fallback != null ? fallback : super.resumeWithFallback();
    }
}
