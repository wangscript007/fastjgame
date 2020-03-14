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

package com.wjybxx.fastjgame.net.binaryextend;

import com.wjybxx.fastjgame.net.binary.CodecRegistry;
import com.wjybxx.fastjgame.net.binary.DataInputStream;
import com.wjybxx.fastjgame.net.binary.DataOutputStream;
import com.wjybxx.fastjgame.net.binary.PojoCodec;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Class}对象序列化工具类
 *
 * @author wjybxx
 * @version 1.0
 * date - 2020/2/18
 * github - https://github.com/hl845740757
 */
public class ClassCodec extends PojoCodec<Class> {

    /**
     * 加载类的缓存 - 寻找类消耗较大，每个线程一份儿缓存
     */
    private static final ThreadLocal<Map<String, Class<?>>> LOAD_CACHE = ThreadLocal.withInitial(HashMap::new);

    public ClassCodec(byte providerId, int classId) {
        super(providerId, classId);
    }

    @Override
    public void encodeBody(@Nonnull DataOutputStream outputStream, @Nonnull Class value, CodecRegistry codecRegistry) throws Exception {
        encodeClass(outputStream, value);
    }

    @Nonnull
    @Override
    public Class decode(@Nonnull DataInputStream inputStream, CodecRegistry codecRegistry) throws Exception {
        final String className = inputStream.readString();
        return decodeClass(className);
    }

    public static void encodeClass(DataOutputStream outputStream, Class<?> value) throws Exception {
        outputStream.writeString(value.getName());
    }

    @Nonnull
    public static Class decodeClass(String className) throws ClassNotFoundException {
        final Map<String, Class<?>> cacheMap = LOAD_CACHE.get();
        final Class<?> cacheClass = cacheMap.get(className);
        if (cacheClass != null) {
            return cacheClass;
        }
        // 必须使用forName，因为某些类是生成的，不能通过类加载器直接加载
        final Class<?> newClass = Class.forName(className);
        cacheMap.put(className, newClass);
        return newClass;
    }

    @Override
    public Class<Class> getEncoderClass() {
        return Class.class;
    }
}
