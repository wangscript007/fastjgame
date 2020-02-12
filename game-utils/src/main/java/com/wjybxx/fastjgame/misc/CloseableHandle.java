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

package com.wjybxx.fastjgame.misc;


import java.io.Closeable;
import java.io.IOException;

/**
 * 资源句柄，不提供访问数据的方法，只提供关闭功能。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/8/6
 * github - https://github.com/hl845740757
 */
public class CloseableHandle implements Closeable {

    private final Closeable resource;

    public CloseableHandle(Closeable resource) {
        this.resource = resource;
    }

    @Override
    public void close() throws IOException {
        resource.close();
    }
}
