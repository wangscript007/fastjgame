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

import com.wjybxx.fastjgame.net.misc.HostAndPort;
import io.netty.channel.Channel;

/**
 * 绑定端口结果
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/8/5
 * github - https://github.com/hl845740757
 */
public class DefaultSocketPort implements SocketPort {

    private final Channel channel;
    private final HostAndPort hostAndPort;

    public DefaultSocketPort(Channel channel, HostAndPort hostAndPort) {
        this.channel = channel;
        this.hostAndPort = hostAndPort;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }

    @Override
    public void close() {
        channel.close();
    }

}
