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

package com.wjybxx.fastjgame.mrg;

import com.google.inject.Inject;
import com.wjybxx.fastjgame.concurrent.ListenableFuture;
import com.wjybxx.fastjgame.concurrent.misc.AbstractThreadLifeCycleHelper;
import com.wjybxx.fastjgame.eventloop.NetEventLoopGroup;
import com.wjybxx.fastjgame.misc.NetContext;

/**
 * 管理当前World拥有的NetContext
 * @author wjybxx
 * @version 1.0
 * date - 2019/8/4
 * github - https://github.com/hl845740757
 */
public class NetContextMrg extends AbstractThreadLifeCycleHelper {

    private NetContext netContext;
    private final WorldInfoMrg worldInfoMrg;
    private final GameEventLoopMrg gameEventLoopMrg;

    @Inject
    public NetContextMrg(WorldInfoMrg worldInfoMrg, GameEventLoopMrg gameEventLoopMrg) {
        this.worldInfoMrg = worldInfoMrg;
        this.gameEventLoopMrg = gameEventLoopMrg;
    }

    @Override
    protected void startImp() throws Exception {
        // 创建上下文
        ListenableFuture<NetContext> contextFuture = gameEventLoopMrg.getNetEventLoopGroup().createContext(worldInfoMrg.getWorldGuid(),
                worldInfoMrg.getWorldType(), gameEventLoopMrg.getEventLoop());
        contextFuture.awaitUninterruptibly();
        netContext = contextFuture.tryGet();
    }

    @Override
    protected void shutdownImp() {
        if (null != netContext) {
            netContext.deregister();
        }
    }

    public NetContext getNetContext() {
        if (null == this.netContext) {
            throw new IllegalStateException();
        }
        return netContext;
    }
}