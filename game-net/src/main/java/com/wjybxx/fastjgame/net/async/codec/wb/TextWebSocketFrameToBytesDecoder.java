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

package com.wjybxx.fastjgame.net.async.codec.wb;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

/**
 * 文本帧->ByteBuf解码器
 * @author wjybxx
 * @version 1.0
 * date - 2019/4/27 22:36
 * github - https://github.com/hl845740757
 */
public class TextWebSocketFrameToBytesDecoder extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    public TextWebSocketFrameToBytesDecoder() {
        // 不释放资源，由下一个handler负责释放
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        ctx.fireChannelRead(msg.content());
    }
}
