/*
 *    Copyright 2019 wjybxx
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.wjybxx.fastjgame.misc;

import com.google.protobuf.*;
import com.wjybxx.fastjgame.annotation.SerializableClass;
import com.wjybxx.fastjgame.annotation.SerializableField;
import com.wjybxx.fastjgame.annotationprocessor.SerializableNumberProcessor2;
import com.wjybxx.fastjgame.enummapper.NumericalEnum;
import com.wjybxx.fastjgame.enummapper.NumericalEnumMapper;
import com.wjybxx.fastjgame.net.common.ProtocolCodec;
import com.wjybxx.fastjgame.utils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 基于反射的协议编解码工具，它基于protoBuf自定义编码格式。
 * 相对于{@link JsonBasedProtocolCodec}传输的数据量要少得多(1/3 - 1/2)，更少的数据量当然带来更快编码速度，
 * 加上网络传输的影响，这个差距会被放大。
 * <p>
 * 建议能单例就单例，能减少内存占用。
 * <p>
 * 反射的好处：
 * 1. 代码量少，功能强大。
 * 2. 可扩展性好。
 * 3. 可理解性更好。
 * 4. 支持多态，支持泛型等高级特性，如果是静态编码，所有的传输对象声明类型必须是最终的编码类型，代码复用能力差，而且维护成本高。
 * <p>
 * 反射的缺点：
 * 1. 反射比正常方法调用性能差一点，也没有差的太多。
 * 2. 大量的拆装箱操作。
 * <p>
 * 其实我心里还是支持用反射，尤其是在现在的架构下，解码操作可以在Netty的IO线程，也可以在NetEventLoop线程（NetEventLoop线程是非常清闲的），
 * 对应用层的性能降低很有限。
 * <p>
 * 自定义协议，不要使用原生带Tag的方法！
 * 突然发现...计算大小的方法不需要了..............
 * <p>
 * 编码格式：
 * <pre>
 *     	基本数据类型		tag + value
 *     	String          tag + length + bytes
 * 		List/Set 		tag + size + element,element....              element 编解码 -- 递归
 * 		Map      		tag + size + key,value,key,value.....         key/Value 编解码 -- 递归
 * 		Message  		tag + messageId + length + bytes
 * 	    Serializable	tag + messageId + fieldNum + field,field....       field 构成: number + tag + data
 * 	    枚举				tag + messageId + number
 * 	    基本类型数组      tag + size + value,value......
 * </pre>
 * 其中messageId用于确定一个唯一的类！也就是{@link MessageMapper}的重要作用。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/8/19
 * github - https://github.com/hl845740757
 */
@Immutable
@ThreadSafe
public class ReflectBasedProtocolCodec implements ProtocolCodec {

    private static final ThreadLocal<byte[]> LOCAL_BUFFER = ThreadLocal.withInitial(() -> new byte[NetUtils.MAX_BUFFER_SIZE]);

    private final MessageMapper messageMapper;
    /**
     * proto message 解析方法
     */
    private final Map<Class<?>, Parser<?>> parserMap;
    /**
     * 普通类的解析信息
     */
    private final Map<Class<?>, ClassDescriptor> descriptorMap;
    /**
     * 枚举解析方法
     */
    private final Map<Class<?>, EnumDescriptor> enumDescriptorMap;
    /**
     * 所有codec映射
     */
    private final NumericalEnumMapper<Codec<?>> codecMapper;
    /**
     * 克隆工具，提供给bean序列化代理类
     */
    private final BeanCloneUtil beanCloneUtil;

    private ReflectBasedProtocolCodec(MessageMapper messageMapper,
                                      Map<Class<?>, Parser<?>> parserMap,
                                      Map<Class<?>, ClassDescriptor> descriptorMap,
                                      Map<Class<?>, EnumDescriptor> enumDescriptorMap) {
        this.messageMapper = messageMapper;
        this.parserMap = parserMap;
        this.descriptorMap = descriptorMap;
        this.enumDescriptorMap = enumDescriptorMap;
        this.beanCloneUtil = new BeanCloneUtilImp();

        codecMapper = EnumUtils.mapping(initValues());
    }

    private Codec<?>[] initValues() {
        // 预估出现的频率排个序
        return new Codec[]{
                new IntegerCodec(),
                new LongCodec(),
                new FloatCodec(),
                new DoubleCodec(),

                new StringCodec(),
                new MessageCodec(),
                new ReferenceCodec(),

                new ListCodec(),
                new MapCodec(),
                new SetCodec(),

                // 字节数组比较常见
                new ByteArrayCodec(),

                // 枚举
                new ProtoEnumCodec(),
                new NumericalEnumCodec(),

                new BoolCodec(),
                new ByteCodec(),
                new ShortCodec(),

                // 其它数组使用较少
                new IntArrayCodec(),
                new LongArrayCodec(),
                new FloatArrayCodec(),
                new DoubleArrayCodec(),
                new CharArrayCodec(),
                // short数组我工作至今都没用过几次。。。
                new ShortArrayCodec(),

                // 正常情况很少使用char
                new CharCodec(),
        };
    }

    @Nonnull
    @Override
    public ByteBuf writeObject(ByteBufAllocator bufAllocator, @Nullable Object object) throws IOException {
        // 这里的测试结果是：拷贝字节数组，比先计算一次大小，再写入ByteBuf快，而且快很多。
        final byte[] localBuffer = LOCAL_BUFFER.get();
        // 减少字节数组创建，即使使用输出流构造，其内部还是做了缓存。
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(localBuffer);
        writeObject(codedOutputStream, object);
        // 真正写入到byteBuf
        final ByteBuf buffer = bufAllocator.buffer(codedOutputStream.getTotalBytesWritten());
        buffer.writeBytes(localBuffer, 0, codedOutputStream.getTotalBytesWritten());
        return buffer;
    }

    private void writeObject(CodedOutputStream outputStream, @Nullable Object object) throws IOException {
        if (object == null) {
            writeTag(outputStream, WireType.NULL);
            return;
        }
        writeRuntimeType(outputStream, object);
    }

    @SuppressWarnings("unchecked")
    private void writeRuntimeType(CodedOutputStream outputStream, @Nonnull Object object) throws IOException {
        final Class<?> type = object.getClass();
        for (Codec codec : codecMapper.values()) {
            if (codec.isSupport(type)) {
                writeTag(outputStream, codec.getWireType());
                codec.writeData(outputStream, object);
                return;
            }
        }
        throw new IOException("unsupported class " + object.getClass().getName());
    }

    @Override
    public Object readObject(ByteBuf data) throws IOException {
        final byte[] localBuffer = LOCAL_BUFFER.get();
        final int readableBytes = data.readableBytes();
        data.readBytes(localBuffer, 0, readableBytes);
        // 减少字节数组创建，即使使用输入流构造，其内部还是做了缓 - 而且编解码速度更快
        CodedInputStream codedInputStream = CodedInputStream.newInstance(localBuffer, 0, readableBytes);
        // 真正读取数据
        return readObject(codedInputStream);
    }

    /**
     * 读取一个对象，先读取一个标记，在读取数据
     *
     * @param inputStream 输入流
     * @return obj
     * @throws IOException error
     */
    @Nullable
    private Object readObject(CodedInputStream inputStream) throws IOException {
        final byte wireType = readTag(inputStream);
        if (wireType == WireType.NULL) {
            return null;
        }
        return getCodec(wireType).readData(inputStream);
    }

    @Nonnull
    private Codec getCodec(int wireType) throws IOException {
        Codec codec = codecMapper.forNumber(wireType);
        if (null == codec) {
            throw new IOException("unsupported wireType " + wireType);
        }
        return codec;
    }

    @Override
    public Object cloneObject(@Nullable Object object) throws IOException {
        if (object == null) {
            return null;
        }
        return cloneRuntimeTypes(object);
    }

    @SuppressWarnings("unchecked")
    private Object cloneRuntimeTypes(@Nonnull Object object) throws IOException {
        final Class<?> type = object.getClass();
        for (Codec codec : codecMapper.values()) {
            if (codec.isSupport(type)) {
                return codec.clone(object);
            }
        }
        throw new IOException("unsupported class " + object.getClass().getName());
    }

    @Nonnull
    @Override
    public byte[] serializeToBytes(@Nullable Object obj) throws IOException {
        // 这里测试也是拷贝字节数组快于先计算大小（两轮反射）
        final byte[] localBuffer = LOCAL_BUFFER.get();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(localBuffer);
        writeObject(codedOutputStream, obj);

        final byte[] resultBytes = new byte[codedOutputStream.getTotalBytesWritten()];
        System.arraycopy(localBuffer, 0, resultBytes, 0, resultBytes.length);
        return resultBytes;
    }

    @Override
    public Object deserializeFromBytes(@Nonnull byte[] data) throws IOException {
        return readObject(CodedInputStream.newInstance(data));
    }

    /**
     * 计算对象的大小
     *
     * @param object 对基本类型进行了装箱操作
     * @return size
     */
    @SuppressWarnings("unchecked")
    private int calSerializeSize(@Nullable Object object) throws IOException {
        if (object == null) {
            return calTagSize(WireType.NULL);
        }
        final Class<?> type = object.getClass();
        for (Codec codec : codecMapper.values()) {
            if (codec.isSupport(type)) {
                return calTagSize(codec.getWireType()) + codec.calSerializeDataSize(object);
            }
        }
        throw new UnsupportedOperationException("un support type " + object.getClass().getName());
    }

    // ------------------------------------------- tag相关 ------------------------------------

    /**
     * 计算tag大小，也就是类型符号的大小，tag都是整数，使用uint32编解码
     *
     * @param wireType 类型
     * @return 序列化后的大小。
     */
    private static int calTagSize(byte wireType) {
        assert wireType >= 0;
        return 1;
    }

    /**
     * 写入一个tag
     *
     * @param outputStream 输出流
     * @param wireType     tag
     * @throws IOException error
     */
    private static void writeTag(CodedOutputStream outputStream, byte wireType) throws IOException {
        outputStream.writeRawByte(wireType);
    }

    /**
     * 读取一个tag
     *
     * @param inputStream 输入流
     * @return tag
     * @throws IOException error
     */
    private static byte readTag(CodedInputStream inputStream) throws IOException {
        return inputStream.readRawByte();
    }

    /**
     * 类文件描述符
     */
    private static class ClassDescriptor {

        private final BeanSerializer serializer;
        /**
         * 构造方法
         */
        private final Constructor constructor;
        /**
         * 要序列化的字段的映射
         */
        private final NumericalEnumMapper<FieldDescriptor> fieldDescriptorMapper;

        private ClassDescriptor(BeanSerializer serializer, FieldDescriptor[] serializableFields, Constructor constructor) {
            this.serializer = serializer;
            this.constructor = constructor;
            this.fieldDescriptorMapper = EnumUtils.mapping(serializableFields);
        }

        private int filedNum() {
            return fieldDescriptorMapper.values().length;
        }
    }

    private static class FieldDescriptor implements NumericalEnum {
        /**
         * 用于反射赋值
         */
        private final Field field;
        /**
         * 字段对应的number，用于兼容性支持，认为一般不会出现负数
         */
        private final int number;
        /**
         * 数据类型缓存 - 可大幅提高编解码速度
         */
        private final byte wireType;

        private FieldDescriptor(Field field, int number, byte wireType) {
            this.field = field;
            this.number = number;
            this.wireType = wireType;
        }

        @Override
        public int getNumber() {
            return number;
        }
    }

    private static class EnumDescriptor {

        /**
         * 解析方法
         */
        private final Method forNumberMethod;

        private EnumDescriptor(Method forNumberMethod) {
            this.forNumberMethod = forNumberMethod;
        }
    }

    private interface Codec<T> extends NumericalEnum {

        /**
         * 是否支持编码该对象
         *
         * @param type 数据类型
         * @return 如果支持则返回true
         */
        boolean isSupport(Class<?> type);

        /**
         * 计算序列化后的大小，不包含wireType
         *
         * @param obj 待计算的对象
         * @return size
         */
        int calSerializeDataSize(@Nonnull T obj) throws IOException;

        /**
         * 编码协议内容，不包含wireType
         *
         * @param outputStream 输出流
         * @param obj          待编码的对象
         * @throws IOException error，
         */
        void writeData(CodedOutputStream outputStream, @Nonnull T obj) throws IOException;

        /**
         * 解码字段协议内容，不包含wireType
         *
         * @param inputStream 输入流
         * @return data
         * @throws IOException error
         */
        T readData(CodedInputStream inputStream) throws IOException;

        @Override
        default int getNumber() {
            return getWireType();
        }

        /**
         * 该codec对应的类型
         *
         * @return wireType
         */
        byte getWireType();

        /**
         * 克隆一个对象
         *
         * @param obj 待克隆的对象
         * @return newInstance or the same object
         */
        T clone(@Nonnull T obj) throws IOException;
    }

    /**
     * 一个字节就占用一个字节 - 不需要使用int32的格式
     */
    private static class ByteCodec implements Codec<Byte> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Byte.class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull Byte obj) throws IOException {
            return 1;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Byte obj) throws IOException {
            outputStream.writeRawByte(obj);
        }

        @Override
        public Byte readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readRawByte();
        }

        @Override
        public byte getWireType() {
            return WireType.BYTE;
        }

        @Override
        public Byte clone(@Nonnull Byte obj) {
            return obj;
        }
    }

    // -------------------------------------------- int32 -----------------------------------------------

    private static abstract class Int32Codec<T extends Number> implements Codec<T> {

        @Override
        public final int calSerializeDataSize(@Nonnull T obj) {
            return CodedOutputStream.computeInt32SizeNoTag(obj.intValue());
        }

        @Override
        public final void writeData(CodedOutputStream outputStream, @Nonnull T obj) throws IOException {
            outputStream.writeInt32NoTag(obj.intValue());
        }

    }

    private static class IntegerCodec extends Int32Codec<Integer> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Integer.class;
        }

        @Override
        public Integer readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readInt32();
        }

        @Override
        public byte getWireType() {
            return WireType.INT;
        }

        @Override
        public Integer clone(@Nonnull Integer obj) {
            return obj;
        }
    }

    private static class ShortCodec extends Int32Codec<Short> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Short.class;
        }

        @Override
        public Short readData(CodedInputStream inputStream) throws IOException {
            return (short) inputStream.readInt32();
        }

        @Override
        public byte getWireType() {
            return WireType.SHORT;
        }

        @Override
        public Short clone(@Nonnull Short obj) {
            return obj;
        }
    }

    private static class CharCodec implements Codec<Character> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Character.class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull Character obj) {
            // char 是无符号整形
            return CodedOutputStream.computeUInt32SizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Character obj) throws IOException {
            outputStream.writeUInt32NoTag(obj);
        }

        @Override
        public Character readData(CodedInputStream inputStream) throws IOException {
            return (char) inputStream.readUInt32();
        }

        @Override
        public byte getWireType() {
            return WireType.CHAR;
        }

        @Override
        public Character clone(@Nonnull Character obj) {
            return obj;
        }
    }

    private static class LongCodec implements Codec<Long> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Long.class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull Long obj) {
            return CodedOutputStream.computeInt64SizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Long obj) throws IOException {
            outputStream.writeInt64NoTag(obj);
        }

        @Override
        public Long readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readInt64();
        }

        @Override
        public byte getWireType() {
            return WireType.LONG;
        }

        @Override
        public Long clone(@Nonnull Long obj) {
            return obj;
        }
    }

    private static class FloatCodec implements Codec<Float> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Float.class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull Float obj) {
            return CodedOutputStream.computeFloatSizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Float obj) throws IOException {
            outputStream.writeFloatNoTag(obj);
        }

        @Override
        public Float readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readFloat();
        }

        @Override
        public byte getWireType() {
            return WireType.FLOAT;
        }

        @Override
        public Float clone(@Nonnull Float obj) {
            return obj;
        }
    }

    private static class DoubleCodec implements Codec<Double> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Double.class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull Double obj) {
            return CodedOutputStream.computeDoubleSizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Double obj) throws IOException {
            outputStream.writeDoubleNoTag(obj);
        }

        @Override
        public Double readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readDouble();
        }

        @Override
        public byte getWireType() {
            return WireType.DOUBLE;
        }

        @Override
        public Double clone(@Nonnull Double obj) {
            return obj;
        }
    }

    private static class BoolCodec implements Codec<Boolean> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == Boolean.class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull Boolean obj) {
            return CodedOutputStream.computeBoolSizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Boolean obj) throws IOException {
            outputStream.writeBoolNoTag(obj);
        }

        @Override
        public Boolean readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readBool();
        }

        @Override
        public byte getWireType() {
            return WireType.BOOLEAN;
        }

        @Override
        public Boolean clone(@Nonnull Boolean obj) {
            return obj;
        }
    }

    private static class StringCodec implements Codec<String> {

        @Override
        public boolean isSupport(Class<?> type) {
            return String.class == type;
        }

        @Override
        public int calSerializeDataSize(@Nonnull String obj) {
            return CodedOutputStream.computeStringSizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull String obj) throws IOException {
            outputStream.writeStringNoTag(obj);
        }

        @Override
        public String readData(CodedInputStream inputStream) throws IOException {
            return inputStream.readString();
        }

        @Override
        public byte getWireType() {
            return WireType.STRING;
        }

        @Override
        public String clone(@Nonnull String obj) {
            return obj;
        }
    }

    // protoBuf消息编解码支持
    // messageId 使用大端模式写入，和json序列化方式一致，也方便客户端解析
    private class MessageCodec implements Codec<AbstractMessage> {

        @Override
        public boolean isSupport(Class<?> type) {
            return AbstractMessage.class.isAssignableFrom(type);
        }

        @Override
        public int calSerializeDataSize(@Nonnull AbstractMessage obj) {
            return 4 + CodedOutputStream.computeMessageSizeNoTag(obj);
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull AbstractMessage obj) throws IOException {
            int messageId = messageMapper.getMessageId(obj.getClass());

            // 大端模式写入一个int
            outputStream.writeRawByte((messageId >>> 24));
            outputStream.writeRawByte((messageId >>> 16));
            outputStream.writeRawByte((messageId >>> 8));
            outputStream.writeRawByte(messageId);

            outputStream.writeMessageNoTag(obj);
        }

        @SuppressWarnings("unchecked")
        @Override
        public AbstractMessage readData(CodedInputStream inputStream) throws IOException {
            // 大端模式读取一个int
            final int messageId = (inputStream.readRawByte() & 0xFF) << 24
                    | (inputStream.readRawByte() & 0xFF) << 16
                    | (inputStream.readRawByte() & 0xFF) << 8
                    | inputStream.readRawByte() & 0xFF;

            final Class<?> messageClazz = messageMapper.getMessageClazz(messageId);
            final Parser<AbstractMessage> parser = (Parser<AbstractMessage>) parserMap.get(messageClazz);
            return inputStream.readMessage(parser, ExtensionRegistryLite.getEmptyRegistry());
        }

        @Override
        public byte getWireType() {
            return WireType.MESSAGE;
        }

        @Override
        public AbstractMessage clone(@Nonnull AbstractMessage obj) {
            return obj;
        }
    }

    private abstract class EnumCodec<T> implements Codec<T> {

        protected abstract int getNumber(T obj);

        @Override
        public int calSerializeDataSize(@Nonnull T obj) {
            return CodedOutputStream.computeSInt32SizeNoTag(messageMapper.getMessageId(obj.getClass())) +
                    CodedOutputStream.computeEnumSizeNoTag(getNumber(obj));
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull T obj) throws IOException {
            outputStream.writeSInt32NoTag(messageMapper.getMessageId(obj.getClass()));
            outputStream.writeEnumNoTag(getNumber(obj));
        }

        @SuppressWarnings("unchecked")
        @Override
        public T readData(CodedInputStream inputStream) throws IOException {
            int messageId = inputStream.readSInt32();
            int number = inputStream.readEnum();
            Class<?> enumClass = messageMapper.getMessageClazz(messageId);
            try {
                return (T) enumDescriptorMap.get(enumClass).forNumberMethod.invoke(null, number);
            } catch (Exception e) {
                throw new IOException(enumClass.getName(), e);
            }
        }

        @Override
        public T clone(@Nonnull T obj) {
            return obj;
        }
    }

    private class ProtoEnumCodec extends EnumCodec<ProtocolMessageEnum> {

        @Override
        public boolean isSupport(Class<?> type) {
            return ProtocolMessageEnum.class.isAssignableFrom(type);
        }

        @Override
        public byte getWireType() {
            return WireType.PROTO_ENUM;
        }

        @Override
        protected int getNumber(ProtocolMessageEnum obj) {
            return obj.getNumber();
        }
    }

    private class NumericalEnumCodec extends EnumCodec<NumericalEnum> {

        @Override
        public boolean isSupport(Class<?> type) {
            return NumericalEnum.class.isAssignableFrom(type);
        }

        @Override
        public byte getWireType() {
            return WireType.NUMBER_ENUM;
        }

        @Override
        protected int getNumber(NumericalEnum obj) {
            return obj.getNumber();
        }

    }

    // 集合支持
    private abstract class CollectionCodec<T extends Collection> implements Codec<T> {

        @Override
        public int calSerializeDataSize(@Nonnull T obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.size());
            if (obj.size() == 0) {
                return totalSize;
            }
            for (Object element : obj) {
                totalSize += ReflectBasedProtocolCodec.this.calSerializeSize(element);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull T obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.size());
            if (obj.size() == 0) {
                return;
            }
            for (Object element : obj) {
                ReflectBasedProtocolCodec.this.writeObject(outputStream, element);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public T readData(CodedInputStream inputStream) throws IOException {
            int size = inputStream.readUInt32();
            if (size == 0) {
                return newCollection(0);
            }
            T collection = newCollection(size);
            for (int index = 0; index < size; index++) {
                collection.add(ReflectBasedProtocolCodec.this.readObject(inputStream));
            }
            return collection;
        }

        /**
         * 当size大于0时，尝试返回一个足够容量的集合
         *
         * @param size 需要的大小
         */
        @Nonnull
        protected abstract T newCollection(int size);

        @SuppressWarnings("unchecked")
        @Override
        public T clone(@Nonnull T obj) throws IOException {
            T collection = newCollection(obj.size());
            for (Object element : obj) {
                collection.add(ReflectBasedProtocolCodec.this.cloneObject(element));
            }
            return collection;
        }
    }

    private class ListCodec extends CollectionCodec<List> {

        @Override
        public boolean isSupport(Class<?> type) {
            return List.class.isAssignableFrom(type);
        }

        @Override
        public byte getWireType() {
            return WireType.LIST;
        }

        @Nonnull
        @Override
        protected List newCollection(int size) {
            return new ArrayList(size);
        }
    }

    private class SetCodec extends CollectionCodec<Set> {

        @Override
        public boolean isSupport(Class<?> type) {
            return Set.class.isAssignableFrom(type);
        }

        @Override
        public byte getWireType() {
            return WireType.SET;
        }

        @Nonnull
        @Override
        protected Set newCollection(int size) {
            return CollectionUtils.newLinkedHashSetWithExpectedSize(size);
        }
    }

    private class MapCodec implements Codec<Map> {

        @Override
        public boolean isSupport(Class<?> type) {
            return Map.class.isAssignableFrom(type);
        }

        @SuppressWarnings("unchecked")
        @Override
        public int calSerializeDataSize(@Nonnull Map obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.size());
            if (obj.size() == 0) {
                return totalSize;
            }
            for (Map.Entry entry : ((Map<Object, Object>) obj).entrySet()) {
                totalSize += ReflectBasedProtocolCodec.this.calSerializeSize(entry.getKey());
                totalSize += ReflectBasedProtocolCodec.this.calSerializeSize(entry.getValue());
            }
            return totalSize;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Map obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.size());
            if (obj.size() == 0) {
                return;
            }
            for (Map.Entry entry : ((Map<Object, Object>) obj).entrySet()) {
                ReflectBasedProtocolCodec.this.writeObject(outputStream, entry.getKey());
                ReflectBasedProtocolCodec.this.writeObject(outputStream, entry.getValue());
            }
        }

        @Override
        public Map readData(CodedInputStream inputStream) throws IOException {
            int size = inputStream.readUInt32();
            if (size == 0) {
                return new LinkedHashMap();
            }
            Map<Object, Object> map = CollectionUtils.newLinkedHashMapWithExpectedSize(size);
            for (int index = 0; index < size; index++) {
                Object key = ReflectBasedProtocolCodec.this.readObject(inputStream);
                Object value = ReflectBasedProtocolCodec.this.readObject(inputStream);
                map.put(key, value);
            }
            return map;
        }

        @Override
        public byte getWireType() {
            return WireType.MAP;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map clone(@Nonnull Map obj) throws IOException {
            Map<Object, Object> map = CollectionUtils.newLinkedHashMapWithExpectedSize(obj.size());
            for (Map.Entry entry : ((Map<Object, Object>) obj).entrySet()) {
                final Object k = ReflectBasedProtocolCodec.this.cloneObject(entry.getKey());
                final Object v = ReflectBasedProtocolCodec.this.cloneObject(entry.getValue());
                map.put(k, v);
            }
            return map;
        }
    }

    /**
     * 带注解的类的编解码器
     */
    private class ReferenceCodec implements Codec<Object> {

        @Override
        public boolean isSupport(Class<?> type) {
            return descriptorMap.containsKey(type);
        }

        @SuppressWarnings("unchecked")
        @Override
        public int calSerializeDataSize(@Nonnull Object obj) throws IOException {
            ClassDescriptor descriptor = descriptorMap.get(obj.getClass());
            int size = CodedOutputStream.computeSInt32SizeNoTag(messageMapper.getMessageId(obj.getClass()));
            size += CodedOutputStream.computeUInt32SizeNoTag(descriptor.filedNum());

            try {
                for (FieldDescriptor fieldDescriptor : descriptor.fieldDescriptorMapper.values()) {
                    size += CodedOutputStream.computeUInt32SizeNoTag(fieldDescriptor.number);

                    Object fieldValue = fieldDescriptor.field.get(obj);
                    // nullable null也需要序列化
                    if (null == fieldValue) {
                        size += calTagSize(WireType.NULL);
                        continue;
                    }
                    // 存在索引的
                    if (fieldDescriptor.wireType != WireType.RUN_TIME) {
                        size += calTagSize(fieldDescriptor.wireType);
                        size += getCodec(fieldDescriptor.wireType).calSerializeDataSize(fieldValue);
                        continue;
                    }
                    // 运行时才知道的类型
                    final Class<?> type = fieldValue.getClass();
                    for (Codec codec : codecMapper.values()) {
                        if (codec.isSupport(type)) {
                            size += calTagSize(codec.getWireType());
                            size += codec.calSerializeDataSize(fieldValue);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                throw new IOException(obj.getClass().getName(), e);
            }
            return size;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull Object obj) throws IOException {
            ClassDescriptor descriptor = descriptorMap.get(obj.getClass());
            outputStream.writeSInt32NoTag(messageMapper.getMessageId(obj.getClass()));

            if (descriptor.serializer != null) {
                descriptor.serializer.write(obj, new OutputStreamImp(outputStream));
            } else {
                writeByReflect(outputStream, obj, descriptor);
            }
        }

        private void writeByReflect(CodedOutputStream outputStream, @Nonnull Object obj, ClassDescriptor descriptor) throws IOException {
            outputStream.writeUInt32NoTag(descriptor.filedNum());
            try {
                for (FieldDescriptor fieldDescriptor : descriptor.fieldDescriptorMapper.values()) {
                    writeField(outputStream, fieldDescriptor, fieldDescriptor.field.get(obj));
                }
            } catch (Exception e) {
                throw new IOException(obj.getClass().getName(), e);
            }
        }

        // tag 和 number必须写入
        @SuppressWarnings("unchecked")
        private void writeField(CodedOutputStream outputStream, FieldDescriptor fieldDescriptor, Object fieldValue) throws IOException {
            outputStream.writeUInt32NoTag(fieldDescriptor.number);

            // null也需要写入，因为新对象的属性不一定也是null
            if (fieldValue == null) {
                writeTag(outputStream, WireType.NULL);
                return;
            }
            // 存在索引的字段
            if (fieldDescriptor.wireType != WireType.RUN_TIME) {
                writeTag(outputStream, fieldDescriptor.wireType);
                getCodec(fieldDescriptor.wireType).writeData(outputStream, fieldValue);
                return;
            }
            // 运行时才知道的类型
            final Class<?> type = fieldValue.getClass();
            for (Codec codec : codecMapper.values()) {
                if (codec.isSupport(type)) {
                    writeTag(outputStream, codec.getWireType());
                    codec.writeData(outputStream, fieldValue);
                    return;
                }
            }
            throw new IOException("unsupported class " + type.getName());
        }

        @Override
        public Object readData(CodedInputStream inputStream) throws IOException {
            final int messageId = inputStream.readSInt32();
            Class<?> messageClass = messageMapper.getMessageClazz(messageId);
            ClassDescriptor descriptor = descriptorMap.get(messageClass);

            if (descriptor.serializer != null) {
                return descriptor.serializer.read(new InputStreamImp(inputStream));
            } else {
                return readByReflect(inputStream, messageClass, descriptor);
            }
        }

        private Object readByReflect(CodedInputStream inputStream, Class<?> messageClass, ClassDescriptor descriptor) throws IOException {
            final int fieldNum = inputStream.readUInt32();
            int index = 1;
            try {
                Object instance = descriptor.constructor.newInstance(ArrayUtils.EMPTY_OBJECT_ARRAY);
                for (; index <= fieldNum; index++) {
                    int number = inputStream.readUInt32();
                    // 兼容性限定：可以少发过来字段，但是不能多发字段
                    FieldDescriptor fieldDescriptor = descriptor.fieldDescriptorMapper.forNumber(number);
                    if (null == fieldDescriptor) {
                        throw new IOException("Incompatible message, field : " + number + " not found");
                    }

                    int wireType = readTag(inputStream);
                    final Object fieldValue = wireType == WireType.NULL ? null : getCodec(wireType).readData(inputStream);
                    // null也需要赋值，默认值不一定是null
                    fieldDescriptor.field.set(instance, fieldValue);
                }
                return instance;
            } catch (Exception e) {
                throw new IOException(messageClass.getName() + ", fieldNumber " + index, e);
            }
        }

        @Override
        public byte getWireType() {
            return WireType.REFERENCE;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object clone(@Nonnull Object obj) throws IOException {
            ClassDescriptor descriptor = descriptorMap.get(obj.getClass());
            if (descriptor.serializer != null) {
                return descriptor.serializer.clone(obj, beanCloneUtil);
            } else {
                return cloneByReflect(obj, descriptor);
            }
        }

        private Object cloneByReflect(@Nonnull Object obj, ClassDescriptor descriptor) throws IOException {
            try {
                Object instance = descriptor.constructor.newInstance(ArrayUtils.EMPTY_OBJECT_ARRAY);
                for (FieldDescriptor fieldDescriptor : descriptor.fieldDescriptorMapper.values()) {
                    // 逐个拷贝
                    final Object fieldValue = fieldDescriptor.field.get(obj);
                    if (null == fieldValue) {
                        // null
                        fieldDescriptor.field.set(instance, null);
                    } else if (fieldDescriptor.wireType != WireType.RUN_TIME) {
                        // 存在类型缓存
                        @SuppressWarnings("unchecked") final Object newValue = getCodec(fieldDescriptor.wireType).clone(fieldValue);
                        fieldDescriptor.field.set(instance, newValue);
                    } else {
                        // 运行时类型
                        final Object newValue = cloneObject(fieldValue);
                        fieldDescriptor.field.set(instance, newValue);
                    }
                }
                return instance;
            } catch (Exception e) {
                throw new IOException(obj.getClass().getName(), e);
            }
        }
    }

    // ------------------------------------------------- 基本数组支持 ---------------------------------------------

    private static class ByteArrayCodec implements Codec<byte[]> {
        @Override
        public boolean isSupport(Class<?> type) {
            return type == byte[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull byte[] obj) throws IOException {
            return CodedOutputStream.computeUInt32SizeNoTag(obj.length) + obj.length;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull byte[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length > 0) {
                outputStream.writeRawBytes(obj);
            }
        }

        @Override
        public byte[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new byte[0];
            }
            return inputStream.readRawBytes(length);
        }

        @Override
        public byte getWireType() {
            return WireType.BYTE_ARRAY;
        }

        @Override
        public byte[] clone(@Nonnull byte[] obj) throws IOException {
            final byte[] result = new byte[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    private static class IntArrayCodec implements Codec<int[]> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == int[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull int[] obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.length);
            if (obj.length == 0) {
                return totalSize;
            }
            for (int value : obj) {
                totalSize += CodedOutputStream.computeSInt32SizeNoTag(value);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull int[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length == 0) {
                return;
            }
            for (int value : obj) {
                outputStream.writeSInt32NoTag(value);
            }
        }

        @Override
        public int[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new int[0];
            }
            int[] result = new int[length];
            for (int index = 0; index < length; index++) {
                result[index] = inputStream.readSInt32();
            }
            return result;
        }

        @Override
        public byte getWireType() {
            return WireType.INT_ARRAY;
        }

        @Override
        public int[] clone(@Nonnull int[] obj) throws IOException {
            final int[] result = new int[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    private static class ShortArrayCodec implements Codec<short[]> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == short[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull short[] obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.length);
            if (obj.length == 0) {
                return totalSize;
            }
            for (short value : obj) {
                totalSize += CodedOutputStream.computeSInt32SizeNoTag(value);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull short[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length == 0) {
                return;
            }
            for (short value : obj) {
                outputStream.writeSInt32NoTag(value);
            }
        }

        @Override
        public short[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new short[0];
            }
            short[] result = new short[length];
            for (int index = 0; index < length; index++) {
                result[index] = (short) inputStream.readSInt32();
            }
            return result;
        }

        @Override
        public byte getWireType() {
            return WireType.SHORT_ARRAY;
        }

        @Override
        public short[] clone(@Nonnull short[] obj) throws IOException {
            final short[] result = new short[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    private static class LongArrayCodec implements Codec<long[]> {
        @Override
        public boolean isSupport(Class<?> type) {
            return type == long[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull long[] obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.length);
            if (obj.length == 0) {
                return totalSize;
            }
            for (long value : obj) {
                totalSize += CodedOutputStream.computeSInt64SizeNoTag(value);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull long[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length == 0) {
                return;
            }
            for (long value : obj) {
                outputStream.writeSInt64NoTag(value);
            }
        }

        @Override
        public long[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new long[0];
            }
            long[] result = new long[length];
            for (int index = 0; index < length; index++) {
                result[index] = inputStream.readSInt64();
            }
            return result;
        }

        @Override
        public byte getWireType() {
            return WireType.LONG_ARRAY;
        }

        @Override
        public long[] clone(@Nonnull long[] obj) throws IOException {
            final long[] result = new long[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    private static class FloatArrayCodec implements Codec<float[]> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == float[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull float[] obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.length);
            if (obj.length == 0) {
                return totalSize;
            }
            for (float value : obj) {
                totalSize += CodedOutputStream.computeFloatSizeNoTag(value);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull float[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length == 0) {
                return;
            }
            for (float value : obj) {
                outputStream.writeFloatNoTag(value);
            }
        }

        @Override
        public float[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new float[0];
            }
            float[] result = new float[length];
            for (int index = 0; index < length; index++) {
                result[index] = inputStream.readFloat();
            }
            return result;
        }

        @Override
        public byte getWireType() {
            return WireType.FLOAT_ARRAY;
        }

        @Override
        public float[] clone(@Nonnull float[] obj) throws IOException {
            final float[] result = new float[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    private static class DoubleArrayCodec implements Codec<double[]> {
        @Override
        public boolean isSupport(Class<?> type) {
            return type == double[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull double[] obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.length);
            if (obj.length == 0) {
                return totalSize;
            }
            for (double value : obj) {
                totalSize += CodedOutputStream.computeDoubleSizeNoTag(value);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull double[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length == 0) {
                return;
            }
            for (double value : obj) {
                outputStream.writeDoubleNoTag(value);
            }
        }

        @Override
        public double[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new double[0];
            }
            double[] result = new double[length];
            for (int index = 0; index < length; index++) {
                result[index] = inputStream.readDouble();
            }
            return result;
        }

        @Override
        public byte getWireType() {
            return WireType.DOUBLE_ARRAY;
        }

        @Override
        public double[] clone(@Nonnull double[] obj) throws IOException {
            final double[] result = new double[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    private static class CharArrayCodec implements Codec<char[]> {

        @Override
        public boolean isSupport(Class<?> type) {
            return type == char[].class;
        }

        @Override
        public int calSerializeDataSize(@Nonnull char[] obj) throws IOException {
            int totalSize = CodedOutputStream.computeUInt32SizeNoTag(obj.length);
            if (obj.length == 0) {
                return totalSize;
            }
            for (char value : obj) {
                totalSize += CodedOutputStream.computeUInt32SizeNoTag(value);
            }
            return totalSize;
        }

        @Override
        public void writeData(CodedOutputStream outputStream, @Nonnull char[] obj) throws IOException {
            outputStream.writeUInt32NoTag(obj.length);
            if (obj.length == 0) {
                return;
            }
            for (char value : obj) {
                outputStream.writeUInt32NoTag(value);
            }
        }

        @Override
        public char[] readData(CodedInputStream inputStream) throws IOException {
            final int length = inputStream.readUInt32();
            if (length == 0) {
                return new char[0];
            }
            char[] result = new char[length];
            for (int index = 0; index < length; index++) {
                result[index] = (char) inputStream.readUInt32();
            }
            return result;
        }

        @Override
        public byte getWireType() {
            return WireType.CHAR_ARRAY;
        }

        @Override
        public char[] clone(@Nonnull char[] obj) throws IOException {
            final char[] result = new char[obj.length];
            System.arraycopy(obj, 0, result, 0, obj.length);
            return result;
        }
    }

    // --------------------------------------------------------------javaBean处理 -----------------------------------------------
    private class InputStreamImp implements BeanInputStream {

        private final CodedInputStream inputStream;

        private InputStreamImp(CodedInputStream inputStream) {
            this.inputStream = inputStream;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T readObject(byte wireType) throws IOException {
            final byte tag = readTag(inputStream);
            if (tag == WireType.NULL) {
                return null;
            }

            if (wireType != WireType.RUN_TIME) {
                if (wireType != tag) {
                    throw new IOException("Incompatible type, expected: " + wireType + ", but read: " + tag);
                }
            }

            return (T) getCodec(wireType).readData(inputStream);
        }
    }

    private class OutputStreamImp implements BeanOutputStream {

        private final CodedOutputStream outputStream;

        private OutputStreamImp(CodedOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void writeObject(byte wireType, @Nullable Object value) throws IOException {
            if (value == null) {
                writeTag(outputStream, WireType.NULL);
                return;
            }

            if (wireType != WireType.RUN_TIME) {
                writeTag(outputStream, wireType);
                getCodec(wireType).writeData(outputStream, value);
                return;
            }

            // 极少走到这里
            writeRuntimeType(outputStream, value);
        }
    }

    private class BeanCloneUtilImp implements BeanCloneUtil {

        @SuppressWarnings("unchecked")
        @Override
        public <T> T clone(byte wireType, @Nullable T value) throws IOException {
            if (value == null) {
                return null;
            }

            if (wireType != WireType.RUN_TIME) {
                return (T) getCodec(wireType).clone(value);
            }

            // 极少走到这里
            return (T) cloneRuntimeTypes(value);
        }
    }

    // ------------------------------------------------- 工厂方法 ------------------------------------------------------
    @Nonnull
    public static ReflectBasedProtocolCodec newInstance(MessageMapper messageMapper) {
        final Map<Class<?>, Parser<?>> parserMap = new IdentityHashMap<>();
        final Map<Class<?>, ClassDescriptor> classDescriptorMap = new IdentityHashMap<>();
        final Map<Class<?>, EnumDescriptor> enumDescriptorMap = new IdentityHashMap<>();

        try {
            // 使用反射一定要setAccessible为true，否则会拖慢性能。
            for (Class<?> messageClazz : messageMapper.getAllMessageClasses()) {
                // protoBuf消息
                if (AbstractMessage.class.isAssignableFrom(messageClazz)) {
                    Parser<?> parser = ProtoUtils.findParser(messageClazz);
                    parserMap.put(messageClazz, parser);
                    continue;
                }
                // protoBufEnum
                if (ProtocolMessageEnum.class.isAssignableFrom(messageClazz)) {
                    indexEnumDescriptor(enumDescriptorMap, messageClazz);
                    continue;
                }
                // NumericalEnum (自定义枚举或伪枚举)
                if (NumericalEnum.class.isAssignableFrom(messageClazz)) {
                    if (messageClazz.isAnnotationPresent(SerializableClass.class)) {
                        indexEnumDescriptor(enumDescriptorMap, messageClazz);
                    }
                    continue;
                }
                // 带有注解的普通类 - 缓存所有的字段描述
                if (messageClazz.isAnnotationPresent(SerializableClass.class)) {
                    indexClassDescriptor(classDescriptorMap, messageClazz);
                }
            }
            return new ReflectBasedProtocolCodec(messageMapper, parserMap, classDescriptorMap, enumDescriptorMap);
        } catch (Exception e) {
            return ConcurrentUtils.rethrow(e);
        }
    }

    private static void indexClassDescriptor(Map<Class<?>, ClassDescriptor> classDescriptorMap, Class<?> messageClazz) throws NoSuchMethodException {
        try {
            final Class<?> serializerClass = Class.forName(messageClazz.getPackageName() + "." + SerializableNumberProcessor2.getSerializerName(messageClazz.getSimpleName()));
            BeanSerializer<?> serializer = (BeanSerializer<?>) serializerClass.getConstructor().newInstance();
            classDescriptorMap.put(messageClazz, new ClassDescriptor(serializer, null, null));
            return;
        } catch (ClassNotFoundException ignore) {

        } catch (Throwable e) {
            ConcurrentUtils.rethrow(e);
        }

        indexClassByReflect(classDescriptorMap, messageClazz);
    }

    private static void indexClassByReflect(Map<Class<?>, ClassDescriptor> classDescriptorMap, Class<?> messageClazz) throws NoSuchMethodException {
        FieldDescriptor[] fieldDescriptors = Arrays.stream(messageClazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(SerializableField.class))
                .sorted(Comparator.comparingInt(field -> field.getAnnotation(SerializableField.class).number()))
                .map(field -> {
                    // 取消检测
                    field.setAccessible(true);
                    SerializableField annotation = field.getAnnotation(SerializableField.class);
                    return new FieldDescriptor(field, annotation.number(), WireType.findType(field.getType()));
                })
                .toArray(FieldDescriptor[]::new);

        // 必须提供无参构造方法
        Constructor constructor = messageClazz.getDeclaredConstructor();
        constructor.setAccessible(true);

        ClassDescriptor classDescriptor = new ClassDescriptor(null, fieldDescriptors, constructor);
        classDescriptorMap.put(messageClazz, classDescriptor);
    }

    private static void indexEnumDescriptor(Map<Class<?>, EnumDescriptor> enumDescriptorMap, Class<?> messageClazz) throws NoSuchMethodException {
        Method forNumberMethod = messageClazz.getMethod("forNumber", int.class);
        // 取消检测
        forNumberMethod.setAccessible(true);
        EnumDescriptor enumDescriptor = new EnumDescriptor(forNumberMethod);
        enumDescriptorMap.put(messageClazz, enumDescriptor);
    }
}
