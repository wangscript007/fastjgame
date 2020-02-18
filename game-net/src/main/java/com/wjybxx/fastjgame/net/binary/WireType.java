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
import com.google.protobuf.ProtocolMessageEnum;
import com.wjybxx.fastjgame.db.annotation.DBEntity;
import com.wjybxx.fastjgame.net.annotation.SerializableClass;
import com.wjybxx.fastjgame.net.exception.BadCollectionDeclarationTypeException;
import com.wjybxx.fastjgame.utils.ClassScanner;
import com.wjybxx.fastjgame.utils.misc.Chunk;
import com.wjybxx.fastjgame.utils.reflect.TypeParameterFinder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * 数据类型
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/8/17
 * github - https://github.com/hl845740757
 */
public class WireType {

    /**
     * 默认{@link Map}的解析类型
     * Q: 为什么要使用{@link LinkedHashMap}？
     * A: 键值对的顺序必须保持和发送方一致。
     */
    public static final Class<?> DEFAULT_MAP_IMPL_CLASS = LinkedHashMap.class;
    /**
     * 默认{@link Collection}的类型
     * Q: 为什么要使用{@link ArrayList}？
     * A: 元素的顺序必须保持和发送方一致，其它类似实现也是可以的。
     */
    public static final Class<?> DEFAULT_COLLECTION_IMPL_CLASS = ArrayList.class;

    /**
     * bean -> beanSerializer (自动生成的beanSerializer 或 手动实现的)
     * 缓存起来，避免大量查找。
     */
    private static final Map<Class<?>, Class<? extends EntitySerializer<?>>> classBeanSerializerMap;

    static {
        final Set<Class<?>> allSerializerClass = ClassScanner.findClasses("com.wjybxx.fastjgame", name -> true, WireType::isBeanSerializer);
        classBeanSerializerMap = new IdentityHashMap<>(allSerializerClass.size());

        for (Class<?> clazz : allSerializerClass) {
            @SuppressWarnings("unchecked") final Class<? extends EntitySerializer<?>> serializerClass = (Class<? extends EntitySerializer<?>>) clazz;

            try {
                final Class<?> beanClass = TypeParameterFinder.findTypeParameterUnsafe(serializerClass, EntitySerializer.class, "T");
                if (beanClass == Object.class) {
                    throw new UnsupportedOperationException("BeanSerializer, must declare type parameter");
                }
                if (classBeanSerializerMap.containsKey(beanClass)) {
                    throw new UnsupportedOperationException(beanClass.getSimpleName() + " has more than one serializer");
                }

                classBeanSerializerMap.put(beanClass, serializerClass);
            } catch (Exception ignore) {

            }
        }
    }

    private static boolean isBeanSerializer(Class<?> c) {
        return c != EntitySerializer.class && EntitySerializer.class.isAssignableFrom(c);
    }

    /**
     * NULL
     */
    public static final byte NULL = 0;

    // ------------------------------------------- 基本类型 -----------------------------
    /**
     * rawByte
     */
    public static final byte BYTE = 1;
    /**
     * uInt
     */
    public static final byte CHAR = 2;
    /**
     * varInt
     */
    public static final byte SHORT = 3;
    /**
     * varInt(不要修改名字)
     */
    public static final byte INT = 4;
    /**
     * varInt64
     */
    public static final byte LONG = 5;
    /**
     * fixed32
     */
    public static final byte FLOAT = 6;
    /**
     * fixed64
     */
    public static final byte DOUBLE = 7;
    /**
     * rawByte
     */
    public static final byte BOOLEAN = 8;

    // ------------------------------------------- 基本类型数组 -----------------------------
    /**
     * 字节数组
     */
    public static final byte BYTE_ARRAY = 9;
    /**
     * char数组
     */
    public static final byte CHAR_ARRAY = 10;
    /**
     * short数组
     */
    public static final byte SHORT_ARRAY = 11;
    /**
     * int数组
     */
    public static final byte INT_ARRAY = 12;
    /**
     * long数组
     */
    public static final byte LONG_ARRAY = 13;
    /**
     * float数组
     */
    public static final byte FLOAT_ARRAY = 14;
    /**
     * double数组
     */
    public static final byte DOUBLE_ARRAY = 15;
    /**
     * boolean数组
     */
    public static final byte BOOLEAN_ARRAY = 16;

    // ------------------------------------------ 特定对象支持 ------------------------------
    /**
     * 字符串 LENGTH_DELIMITED
     */
    public static final byte STRING = 17;

    /**
     * protobuf的Message LENGTH_DELIMITED
     */
    public static final byte PROTO_MESSAGE = 18;
    /**
     * protoBuf的枚举
     */
    public static final byte PROTO_ENUM = 19;
    /**
     * 自定义数据块
     */
    public static final byte CHUNK = 20;

    // ------------------------------------------ 默认集合支持 ---------------------------------

    /**
     * 默认Map支持
     * 如果一个字段/参数的声明类型是{@link Map}的子类，且是{@link LinkedHashMap}的超类时，那么适用该类型
     */
    public static final byte DEFAULT_MAP = 21;

    /**
     * 如果一个字段的声明类型是{@link List}的子类，且是{@link ArrayList}的超类时，那么适用该字段。
     */
    public static final byte DEFAULT_LIST = 22;
    /**
     * 如果一个字段的声明类型是{@link Set}的子类时，那么适用该类型。
     */
    public static final byte DEFAULT_SET = 23;
    /**
     * 如果一个字段的声明类型是{@link Queue}的子类时时，将默认适用
     */
    public static final byte DEFAULT_QUEUE = 24;

    /**
     * 默认集合支持
     * 如果一个字段/参数的声明类型是{@link Collection}，那么那么适用该类型。
     */
    public static final byte DEFAULT_COLLECTION = 25;

    // ------------------------------------------- 带有注解的类 -----------------------------

    /**
     * 带有{@link DBEntity} 或 {@link SerializableClass}注解的类，
     * 或手动实现{@link EntitySerializer}负责解析的类。
     */
    public static final byte CUSTOM_ENTITY = 28;


    /**
     * 动态类型 - 运行时才能确定的类型（它是标记类型）
     */
    public static final byte RUN_TIME = 29;

    /**
     * 查找一个class对应的wireType
     *
     * @param declaredType 字段的声明类型
     * @return wireType
     */
    public static byte findType(@Nonnull final Class<?> declaredType) {
        // --- 基本类型
        if (declaredType == byte.class || declaredType == Byte.class) {
            return WireType.BYTE;
        }
        if (declaredType == char.class || declaredType == Character.class) {
            return WireType.CHAR;
        }
        if (declaredType == short.class || declaredType == Short.class) {
            return WireType.SHORT;
        }
        if (declaredType == int.class || declaredType == Integer.class) {
            return WireType.INT;
        }
        if (declaredType == long.class || declaredType == Long.class) {
            return WireType.LONG;
        }
        if (declaredType == float.class || declaredType == Float.class) {
            return WireType.FLOAT;
        }
        if (declaredType == double.class || declaredType == Double.class) {
            return WireType.DOUBLE;
        }
        if (declaredType == boolean.class || declaredType == Boolean.class) {
            return WireType.BOOLEAN;
        }

        // ---- 基本类型数组
        if (declaredType == byte[].class) {
            return WireType.BYTE_ARRAY;
        }
        if (declaredType == char[].class) {
            return WireType.CHAR_ARRAY;
        }
        if (declaredType == short[].class) {
            return WireType.SHORT_ARRAY;
        }
        if (declaredType == int[].class) {
            return WireType.INT_ARRAY;
        }
        if (declaredType == long[].class) {
            return WireType.LONG_ARRAY;
        }
        if (declaredType == float[].class) {
            return WireType.FLOAT_ARRAY;
        }
        if (declaredType == double[].class) {
            return WireType.DOUBLE_ARRAY;
        }
        if (declaredType == boolean[].class) {
            return WireType.BOOLEAN_ARRAY;
        }

        // 字符串
        if (declaredType == String.class) {
            return WireType.STRING;
        }

        // protoBuf
        if (AbstractMessage.class.isAssignableFrom(declaredType)) {
            return WireType.PROTO_MESSAGE;
        }

        // protoBuf的枚举
        if (ProtocolMessageEnum.class.isAssignableFrom(declaredType)) {
            return WireType.PROTO_ENUM;
        }

        // CHUNK
        if (declaredType == Chunk.class) {
            return WireType.CHUNK;
        }

        // Map
        if (Map.class.isAssignableFrom(declaredType)) {
            if (declaredType == Map.class) {
                return WireType.DEFAULT_MAP;
            }
            throw new BadCollectionDeclarationTypeException();
        }

        // Collection
        if (Collection.class.isAssignableFrom(declaredType)) {
            if (declaredType == Collection.class) {
                return WireType.DEFAULT_COLLECTION;
            }
            throw new BadCollectionDeclarationTypeException();
        }

        // 有serializer的类型，无论手写的还是自动生成的
        if (classBeanSerializerMap.containsKey(declaredType)) {
            return WireType.CUSTOM_ENTITY;
        }

        // Object，或一些接口，超类等等
        return WireType.RUN_TIME;
    }

    /**
     * 获取消息类对应的序列化辅助类
     *
     * @param messageClazz 消息类
     * @return 序列化辅助类
     */
    @Nullable
    @SuppressWarnings("unchecked")
    static <T> Class<? extends EntitySerializer<T>> getBeanSerializer(Class<T> messageClazz) {
        return (Class<? extends EntitySerializer<T>>) classBeanSerializerMap.get(messageClazz);
    }

    /**
     * 判断一个类是否可以被序列化
     */
    public static boolean isSerializable(Class<?> messageClazz) {
        return classBeanSerializerMap.containsKey(messageClazz)
                || AbstractMessage.class.isAssignableFrom(messageClazz)
                || ProtocolMessageEnum.class.isAssignableFrom(messageClazz);
    }
}