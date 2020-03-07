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

package com.wjybxx.fastjgame.net.http;

import com.wjybxx.fastjgame.utils.concurrent.EventLoop;
import com.wjybxx.fastjgame.utils.concurrent.FailedFutureListener;
import com.wjybxx.fastjgame.utils.concurrent.FutureListener;
import com.wjybxx.fastjgame.utils.concurrent.SucceededFutureListener;
import com.wjybxx.fastjgame.utils.concurrent.adapter.CompletableFutureAdapter;
import com.wjybxx.fastjgame.utils.concurrent.timeout.TimeoutFutureListener;

import javax.annotation.Nonnull;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author wjybxx
 * @version 1.0
 * date - 2020/1/10
 * github - https://github.com/hl845740757
 */
public class DefaultHttpFuture<V> extends CompletableFutureAdapter<V> implements HttpFuture<V> {

    public DefaultHttpFuture(EventLoop executor, CompletableFuture<V> future) {
        super(executor, future);
    }

    @Override
    public boolean isTimeout() {
        return cause() instanceof HttpTimeoutException;
    }

    // ---------------------------------------- 流式语法支持 ---------------------------------
    @Override
    public HttpFuture<V> await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public HttpFuture<V> awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    public HttpFuture<V> onComplete(@Nonnull FutureListener<? super V> listener) {
        super.onComplete(listener);
        return this;
    }

    @Override
    public HttpFuture<V> onComplete(@Nonnull FutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        super.onComplete(listener, bindExecutor);
        return this;
    }

    @Override
    public HttpFuture<V> onSuccess(@Nonnull SucceededFutureListener<? super V> listener) {
        super.onSuccess(listener);
        return this;
    }

    @Override
    public HttpFuture<V> onSuccess(@Nonnull SucceededFutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        super.onSuccess(listener, bindExecutor);
        return this;
    }

    @Override
    public HttpFuture<V> onFailure(@Nonnull FailedFutureListener<? super V> listener) {
        super.onFailure(listener);
        return this;
    }

    @Override
    public HttpFuture<V> onFailure(@Nonnull FailedFutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        super.onFailure(listener, bindExecutor);
        return this;
    }

    @Override
    public HttpFuture<V> onTimeout(@Nonnull TimeoutFutureListener<? super V> listener) {
        super.onComplete(listener);
        return this;
    }

    @Override
    public HttpFuture<V> onTimeout(@Nonnull TimeoutFutureListener<? super V> listener, @Nonnull Executor bindExecutor) {
        super.onComplete(listener, bindExecutor);
        return this;
    }
}
