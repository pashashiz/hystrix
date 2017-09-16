package com.ps;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommand.Setter;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.netflix.hystrix.HystrixCommandGroupKey.Factory.asKey;
import static java.util.stream.IntStream.range;

public class HystrixTest {

    @Test
    public void simpleBlocking() throws Exception {
        // both will be executed in the same thread group "test"
        System.out.println(new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> Thread.currentThread().getName() + " -> test-1").execute());
        System.out.println(new SimpleHystrixCommand<>(HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> Thread.currentThread().getName() + " -> test-2").execute());
    }

    @Test
    public void simpleAsync() throws Exception {
        Future<String> result = new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> Thread.currentThread().getName() + " -> test-1").queue();
        System.out.println(result.get());
    }

    @Test
    public void simpleReactive() throws Exception {
        // observalble is executed on its thread
        // 1. COLD: toObservable()
        // 2. HOT: observe()
        Observable<String> result = new SimpleObservableHystrixCommand<>(
                HystrixObservableCommand.Setter.withGroupKey(asKey("test")),
                Observable.timer(100, TimeUnit.MILLISECONDS)
                        .map(t -> Thread.currentThread().getName() + " -> test-1")).toObservable();
        System.out.println(result.toBlocking().first());
    }

    @Test(expected = HystrixRuntimeException.class)
    public void blockingFailsWithError() throws Exception {
        new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> {
                    throw new RuntimeException("error");
                }).execute();
    }

    @Test(expected = ExecutionException.class)
    public void asyncFailsWithError() throws Exception {
        new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> {
                    throw new RuntimeException("error");
                }).queue().get();
    }

    @Test
    public void reactiveFailsWithError() throws Exception {
        new SimpleObservableHystrixCommand<String>(
                HystrixObservableCommand.Setter.withGroupKey(asKey("test")),
                Observable.error(new RuntimeException("error"))).toObservable()
            .doOnError(throwable -> System.out.println(throwable.getMessage()))
            .subscribe();
        Thread.sleep(1000);
    }

    @Test
    public void blockingFailsWithFallback() throws Exception {
        String result = new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> {
                    throw new RuntimeException("error");
                }, () -> "fallback").execute();
        System.out.println(result);
    }

    @Test
    public void asyncFailsWithFallback() throws Exception {
        String result = new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> {
                    throw new RuntimeException("error");
                }, () -> "fallback").queue().get();
        System.out.println(result);
    }

    @Test
    public void reactiveFailsWithFallback() throws Exception {
        String result = new SimpleObservableHystrixCommand<>(
                HystrixObservableCommand.Setter.withGroupKey(asKey("test")),
                Observable.error(new RuntimeException("error")),
                Observable.just("fallback")).toObservable()
            .toBlocking().first();
        System.out.println(result);
    }

    @Test(expected = HystrixRuntimeException.class)
    public void blockingFailsWhenOverloaded() throws Exception {
        // after 10 tasks are schedules - reject the rest
        range(0, 20).forEach(i -> new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                    () -> {
                        System.out.println("running-" + i);
                        Thread.sleep(5);
                        System.out.println("done-" + i);
                        return "test-" + i;
                    }).queue()
        );
    }

    @Test(expected = HystrixRuntimeException.class)
    public void blockingFailsWhenTimeout() throws Exception {
        // fails after 1 sec
        new SimpleHystrixCommand<>(
                HystrixCommand.Setter.withGroupKey(asKey("test")),
                () -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        System.out.println("interrupted");
                    }
                    return "test";
                }).execute();
    }

    @Test
    public void reacriveFailsWhenOverloaded() throws Exception {
        // for observable there is no semaphore
        range(0, 20).forEach(i -> new SimpleObservableHystrixCommand<>(
                HystrixObservableCommand.Setter.withGroupKey(asKey("test")),
                Observable.create(subscriber -> {
                    try {
                        System.out.println("running-" + i);
                        Thread.sleep(5);
                        subscriber.onNext("test");
                        subscriber.onCompleted();
                        System.out.println("done-" + i);
                    } catch (InterruptedException e) {
                        System.out.println("interrupted");
                    }
                }).subscribeOn(Schedulers.io())).toObservable()
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .toBlocking().first()
        );
    }

    @Test
    public void reacriveFailsWhenTimeout() throws Exception {
        // fails after 1 sec
        new SimpleObservableHystrixCommand<>(
                HystrixObservableCommand.Setter.withGroupKey(asKey("test")),
                Observable.timer(5000, TimeUnit.MILLISECONDS)
                        .map(t -> Thread.currentThread().getName() + " -> test-1")).toObservable()
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .subscribe();
        Thread.sleep(2000);
    }

}
