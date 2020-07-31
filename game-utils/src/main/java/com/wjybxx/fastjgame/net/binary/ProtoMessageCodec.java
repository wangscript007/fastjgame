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

package com.wjybxx.fastjgame.net.binary;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Parser;

/**
 * protoBuf消息编解码支持
 * <p>
 * messageId 使用大端模式写入，和json序列化方式一致，也方便客户端解析
 *
 * @author wjybxx
 * @version 1.0
 * date - 2020/2/17
 */
public class ProtoMessageCodec<T extends AbstractMessage> implements PojoCodecImpl<T> {

    private final Class<T> messageClass;
    private final Parser<T> parser;

    public ProtoMessageCodec(Class<T> messageClass, Parser<T> parser) {
        this.messageClass = messageClass;
        this.parser = parser;
    }

    @Override
    public Class<T> getEncoderClass() {
        return messageClass;
    }

    @Override
    public T readObject(ObjectReader reader) throws Exception {
        return reader.readMessage(parser);
    }

    @Override
    public void writeObject(T instance, ObjectWriter writer) throws Exception {
        writer.writeMessage(instance);
    }
}