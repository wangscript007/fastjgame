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

package com.wjybxx.fastjgame.net.rpc;

import com.wjybxx.fastjgame.net.session.SessionDuplexHandlerAdapter;
import com.wjybxx.fastjgame.net.session.SessionHandlerContext;
import com.wjybxx.fastjgame.util.concurrent.ConcurrentUtils;

/**
 * 单向消息支持
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/9/26
 * github - https://github.com/hl845740757
 */
public class OneWaySupportHandler extends SessionDuplexHandlerAdapter {

    public OneWaySupportHandler() {
    }

    @Override
    public void read(SessionHandlerContext ctx, Object msg) {
        if (msg instanceof OneWayMessage) {
            // 读取到一个单向消息
            OneWayMessage oneWayMessage = (OneWayMessage) msg;
            ConcurrentUtils.safeExecute(ctx.appEventLoop(),
                    new OneWayProcessTask(ctx.session(), oneWayMessage.getBody()));
        } else {
            ctx.fireRead(msg);
        }
    }

    @Override
    public void write(SessionHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof OneWayInvocationTask) {
            // 单向消息
            OneWayInvocationTask task = (OneWayInvocationTask) msg;
            ctx.fireWrite(new OneWayMessage(task.getMessage()));
        } else {
            ctx.fireWrite(msg);
        }
    }
}
