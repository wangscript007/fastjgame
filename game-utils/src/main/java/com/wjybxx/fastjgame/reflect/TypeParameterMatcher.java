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

package com.wjybxx.fastjgame.reflect;

import javax.annotation.Nonnull;

/**
 * 类型参数匹配器
 *
 * @author wjybxx
 * @version 1.0
 * date - 2019/9/26
 * github - https://github.com/hl845740757
 */
public abstract class TypeParameterMatcher {

    /**
     * 查询实例是否与泛型参数匹配
     *
     * @param instance 待检测对象
     * @return true/false
     */
    public abstract boolean matchInstance(@Nonnull Object instance);

    /**
     * 查询指定类是否是泛型参数的子类
     *
     * @param cls 待检查的class对象
     */
    public abstract boolean matchClass(Class<?> cls);

    /**
     * 查找指定泛型参数对应的类型匹配器。
     *
     * @param instance              superClazzOrInterface的子类实例
     * @param superClazzOrInterface 泛型参数typeParamName存在的类,class或interface
     * @param typeParamName         泛型参数名字
     * @param <T>                   约束必须有继承关系或实现关系
     * @return 如果定义的泛型存在，则返回对应的泛型clazz
     */
    public static <T> TypeParameterMatcher findTypeMatcher(@Nonnull T instance,
                                                           @Nonnull Class<? super T> superClazzOrInterface,
                                                           @Nonnull String typeParamName) {
        final Class<?> type = TypeParameterFinder.findTypeParameter(instance, superClazzOrInterface, typeParamName);
        if (type == Object.class) {
            return ObjectTypeMatcher.INSTANCE;
        } else {
            return new ReflectiveTypeMatcher(type);
        }
    }

    private static class ReflectiveTypeMatcher extends TypeParameterMatcher {

        private final Class<?> type;

        private ReflectiveTypeMatcher(Class<?> type) {
            this.type = type;
        }

        @Override
        public boolean matchInstance(@Nonnull Object instance) {
            return type.isInstance(instance);
        }

        @Override
        public boolean matchClass(Class<?> cls) {
            return type.isAssignableFrom(cls);
        }
    }

    private static class ObjectTypeMatcher extends TypeParameterMatcher {

        private static final ObjectTypeMatcher INSTANCE = new ObjectTypeMatcher();

        private ObjectTypeMatcher() {
        }

        @Override
        public boolean matchInstance(@Nonnull Object instance) {
            return true;
        }

        @Override
        public boolean matchClass(Class<?> cls) {
            return true;
        }
    }

}
