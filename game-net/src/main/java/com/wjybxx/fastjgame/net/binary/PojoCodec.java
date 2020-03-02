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

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * 简单对象编解码器
 * Q: {@link #getProviderId()}{@link #getClassId()}的作用？
 * A: 实现自解释性，接收方在解码时可以根据这两个值确定唯一的codec，也就知道了如何解码。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2020/2/25
 */
public abstract class PojoCodec<T> implements Codec<T> {

    private final int providerId;
    private final int classId;

    protected PojoCodec(int providerId, int classId) {
        this.providerId = providerId;
        this.classId = classId;
    }

    @Override
    public final void encode(@Nonnull CodedOutputStream outputStream, @Nonnull T value, CodecRegistry codecRegistry) throws Exception {
        BinaryProtocolCodec.writeTag(outputStream, Tag.POJO);
        outputStream.writeInt32NoTag(getProviderId());
        outputStream.writeInt32NoTag(getClassId());
        encodeBody(outputStream, value, codecRegistry);
    }

    protected abstract void encodeBody(CodedOutputStream outputStream, T value, CodecRegistry codecRegistry) throws Exception;

    /**
     * 返回codec所属的{@link CodecProvider}的id
     */
    public final int getProviderId() {
        return providerId;
    }

    /**
     * 获取{@link #getEncoderClass()}在{@link CodecProvider}下的唯一id
     */
    public final int getClassId() {
        return classId;
    }

    @Override
    public abstract Class<T> getEncoderClass();

    static PojoCodec<?> getPojoCodec(CodedInputStream inputStream, CodecRegistry codecRegistry) throws IOException {
        final int providerId = inputStream.readInt32();
        final int classId = inputStream.readInt32();
        return codecRegistry.getPojoCodec(providerId, classId);
    }
}