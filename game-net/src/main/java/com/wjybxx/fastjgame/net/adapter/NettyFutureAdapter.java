/*
 * Copyright 2019 wjybxx
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wjybxx.fastjgame.net.adapter;

import com.wjybxx.fastjgame.utils.concurrent.*;
import io.netty.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.*;

/**
 * 对Netty的{@link Future}进行适配 - 主要是适配监听器。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/10/1
 * github - https://github.com/hl845740757
 */
public final class NettyFutureAdapter<V> extends AbstractListenableFuture<V> {

    private final EventLoop executor;
    private final Future<V> future;

    /**
     * @param executor 异步执行的默认executor
     */
    public NettyFutureAdapter(@Nonnull EventLoop executor, Future<V> future) {
        this.executor = executor;
        this.future = future;
    }

    @Override
    public boolean isSuccess() {
        return future.isSuccess();
    }

    @Override
    public boolean isCancellable() {
        return future.isCancellable();
    }

    @Nullable
    @Override
    public Throwable cause() {
        return future.cause();
    }

    @Override
    public V get() throws InterruptedException, CompletionException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    @Override
    public V get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, CompletionException, TimeoutException {
        try {
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    @Nullable
    @Override
    public V getNow() {
        return future.getNow();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning) || future.isCancelled();
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    // ---------------------------------------------------------------------------------------------
    @Override
    public ListenableFuture<V> await() throws InterruptedException {
        future.await();
        return this;
    }

    @Override
    public ListenableFuture<V> awaitUninterruptibly() {
        future.awaitUninterruptibly();
        return this;
    }

    @Override
    public boolean await(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return future.await(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, @Nonnull TimeUnit unit) {
        return future.awaitUninterruptibly(timeout, unit);
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public EventLoop defaultExecutor() {
        return executor;
    }

    private void addListener0(@Nonnull FutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        // 不要内联该对象 - lambda表达式捕获的对象不一样
        final FutureListenerEntry<? super V> listenerEntry = new FutureListenerEntry<>(listener, bindExecutor);
        future.addListener(future -> DefaultPromise.notifyListenerNowSafely(this, listenerEntry));
    }

    @Override
    public ListenableFuture<V> onComplete(@Nonnull FutureListener<? super V> listener) {
        addListener0(listener, executor);
        return this;
    }

    @Override
    public ListenableFuture<V> onComplete(@Nonnull FutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        addListener0(listener, bindExecutor);
        return this;
    }

    @Override
    public ListenableFuture<V> onSuccess(@Nonnull SucceededFutureListener<? super V> listener) {
        addListener0(listener, executor);
        return this;
    }

    @Override
    public ListenableFuture<V> onSuccess(@Nonnull SucceededFutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        addListener0(listener, bindExecutor);
        return this;
    }

    @Override
    public ListenableFuture<V> onFailure(@Nonnull FailedFutureListener<? super V> listener) {
        addListener0(listener, executor);
        return this;
    }

    @Override
    public ListenableFuture<V> onFailure(@Nonnull FailedFutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        addListener0(listener, bindExecutor);
        return this;
    }
}
