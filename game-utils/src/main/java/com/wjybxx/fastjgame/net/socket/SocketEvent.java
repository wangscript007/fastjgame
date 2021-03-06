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
package com.wjybxx.fastjgame.net.socket;

import io.netty.channel.Channel;

/**
 * 网络事件参数，提供统一的抽象(窄)视图。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/4/27 9:16
 * github - https://github.com/hl845740757
 */
public interface SocketEvent {

    /**
     * 获取网络事件对于的channel
     */
    Channel channel();

    /**
     * 事件对应的session标识
     */
    String sessionId();

}
