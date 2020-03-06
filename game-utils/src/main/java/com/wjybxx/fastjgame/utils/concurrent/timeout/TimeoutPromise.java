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

package com.wjybxx.fastjgame.utils.concurrent.timeout;

import com.wjybxx.fastjgame.utils.concurrent.FutureListener;
import com.wjybxx.fastjgame.utils.concurrent.Promise;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 具有时效性的Promise
 *
 * @author wjybxx
 * @version 1.0
 * date - 2020/1/6
 * github - https://github.com/hl845740757
 */
public interface TimeoutPromise<V> extends TimeoutFuture<V>, Promise<V> {

    /**
     * 获取毫秒级别的过期时间戳
     */
    long getExpireMillis();

    /**
     * 获取超时时间
     */
    long getExpire(TimeUnit timeUnit);

    @Override
    TimeoutPromise<V> await() throws InterruptedException;

    @Override
    TimeoutPromise<V> awaitUninterruptibly();

    @Override
    TimeoutPromise<V> onComplete(@Nonnull FutureListener<? super V> listener);

    @Override
    TimeoutPromise<V> onComplete(@Nonnull FutureListener<? super V> listener, @Nonnull Executor bindExecutor);

    @Override
    TimeoutPromise<V> removeListener(@Nonnull FutureListener<? super V> listener);
}
