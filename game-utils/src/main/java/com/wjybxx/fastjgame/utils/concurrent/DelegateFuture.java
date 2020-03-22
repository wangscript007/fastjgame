/*
 *  Copyright 2019 wjybxx
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to iBn writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.wjybxx.fastjgame.utils.concurrent;

import com.wjybxx.fastjgame.utils.annotation.UnstableApi;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;

/**
 * @author wjybxx
 * @version 1.0
 * date - 2020/3/10
 */
public class DelegateFuture<V> implements ListenableFuture<V> {

    private final ListenableFuture<V> delegate;

    public DelegateFuture(ListenableFuture<V> delegate) {
        this.delegate = delegate;
    }

    protected ListenableFuture<V> getDelegate() {
        return delegate;
    }

    @Override
    public final boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public final boolean isSuccess() {
        return delegate.isSuccess();
    }

    @Override
    public final boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public final boolean isCancellable() {
        return delegate.isCancellable();
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public final V getNow() {
        return delegate.getNow();
    }

    @Override
    public final Throwable cause() {
        return delegate.cause();
    }

    @Override
    public final EventLoop defaultExecutor() {
        return delegate.defaultExecutor();
    }

    @Override
    @UnstableApi
    public final boolean isVoid() {
        return delegate.isVoid();
    }

    /**
     * @return 必须返回this，声明为{@link DelegateFuture}可以避免错误的返回值
     */
    @Override
    public DelegateFuture<V> addListener(@Nonnull FutureListener<? super V> listener) {
        delegate.addListener(listener);
        return this;
    }

    @Override
    public DelegateFuture<V> addListener(@Nonnull FutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        delegate.addListener(listener, bindExecutor);
        return this;
    }

}