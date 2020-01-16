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

package com.wjybxx.fastjgame.misc;

import com.wjybxx.fastjgame.utils.ClassScanner;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Set;

/**
 * 基于hash的消息映射方法，由类的简单名计算hash值。
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/5/15 15:21
 * github - https://github.com/hl845740757
 */
public class HashMessageMappingStrategy implements MessageMappingStrategy {

    /**
     * 扫描的包名
     */
    private final String packageName;

    public HashMessageMappingStrategy() {
        this("com.wjybxx.fastjgame");
    }

    public HashMessageMappingStrategy(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public Object2IntMap<Class<?>> mapping() throws Exception {
        final Set<Class<?>> allClass = ClassScanner.findClasses(packageName,
                name -> true,
                WireType::isSerializable);

        final Object2IntMap<Class<?>> messageClass2IdMap = new Object2IntOpenHashMap<>(allClass.size());
        for (Class<?> messageClass : allClass) {
            messageClass2IdMap.put(messageClass, getUniqueId(messageClass));
        }

        return messageClass2IdMap;
    }

    public static int getUniqueId(Class<?> messageClass) {
        // 不能直接使用hashCode，直接使用hashCode，在不同的进程的值是不一样的
        // 为什么要simple Name? protoBuf的消息的名字就是java的类名，也方便前端计算该值 - 相同的hash算法即可
        return messageClass.getSimpleName().hashCode();
    }
}
