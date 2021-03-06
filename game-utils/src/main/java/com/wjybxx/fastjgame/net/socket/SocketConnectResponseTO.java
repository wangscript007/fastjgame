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

/**
 * 建立链接应答对应的传输对象
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/10/2
 * github - https://github.com/hl845740757
 */
public interface SocketConnectResponseTO {

    /**
     * 服务器的初始sequence
     * 1. 用于首次建立连接时。
     * 2. 不从0开始，可以降低出现脏连接的情况。
     */
    long getInitSequence();

    /**
     * 服务端期望的下一个消息号
     * (始终校验)
     */
    long getAck();

    /**
     * 是否是关闭连接
     */
    boolean isClose();

    /**
     * 建立连接应答
     */
    SocketConnectResponse getConnectResponse();
}
