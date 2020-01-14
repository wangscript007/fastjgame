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

package com.wjybxx.fastjgame.concurrent;

import java.util.concurrent.CancellationException;

/**
 * @author wjybxx
 * @version 1.0
 * date - 2020/1/8
 * github - https://github.com/hl845740757
 */
public class DefaultFutureResult<V> implements FutureResult<V> {

    private final V result;
    private final Throwable cause;

    public DefaultFutureResult(V result, Throwable cause) {
        this.result = result;
        this.cause = cause;
    }

    @Override
    public final V getNow() {
        return result;
    }

    @Override
    public final Throwable cause() {
        return cause;
    }

    @Override
    public final boolean isSuccess() {
        return cause == null;
    }

    @Override
    public final boolean isCancelled() {
        return cause instanceof CancellationException;
    }
}