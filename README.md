### fastjgame
fastjgame 为 fast java game framework的缩写，如名字一样，该项目的目标是一个高性能，高稳定性的游戏服务器架构。  

### 暂不建议fork，欢迎star和watch
由于个人经验及能力原因，代码改动频繁，甚至很多设计都会推翻，因此暂时不建议fork，但相信我，一定对得起你的star！

***
### 项目里我要写什么？
能复用的底层技术，目前我总结包括：
1. 线程模型。
2. 网络层(rpc、http、序列化)
3. 游戏日志搜集
4. guid(唯一id)生成方案
5. 服务器代码热更新方案
6. 服务器发现

***
### 线程模型
线程模型从Netty借鉴了许多，但也有稍许区别：
1. 拥抱JDK8的CompletableFuture，构建异步管道可以极大的提高代码的表达力，同时降低编程难度。
2. 删除EventExecutor概念，只保留EventLoop概念，EventLoop就是单线程的。如果不需要单线程带来的一些保证，那么使用Executor概念即可。
3. EventLoop是**多生产者单消费者**模型。
4. 提供了基于Disruptor的高性能事件循环**DisruptorEventLoop**。
5. 提供了基于JCTools的Mpsc队列的高性能事件循环**TemplateEventLoop**。

### RPC
这里的rpc设计并未追求所谓的标准，而更追求实用性，主要特点如下：
1. 为客户端生成的proxy类的方法签名与原方法签名可能并不一样，因此proxy类并不实现接口，也不要求被代理的类必须是接口。
2. 所有的proxy类仅仅是辅助类，方法都是静态的，仅仅用于封装请求参数，并不直接执行调用，用户需要显式的通过RpcClient发送请求。
3. 通过short类型的serviceId和methodId定位被调用方法。
4. **使用注解处理器生成调用代码(lambda表达式)**，代替常见反射调用实现。

设计目的是什么？
1. 允许参数不一致，可以更好的支持服务器异步编程。如果方法的返回类型为Future，表示服务器**可能**无法立即返回结果，代理方法会自动监听Future的结果。  
2. 允许参数不一致，可以延迟某些参数的序列化或提前某些参数的反序列化，可以消除请求方和执行方的序列化反序列化工作。该实现依赖**LazySerializable**和**PreDeserializable**注解。  
3. proxy仅仅是辅助类，通过rpcClient发送请求。用户可以选择是**单项通知**、**异步调用**还是**同步调用**。这非常像ExecutorService中的**execute**和**submit**，
用户可以自由选择是否监听执行结果。放弃**透明性**，其实是提醒用户rpc和普通方法调用存在明显的性能差异，鼓励用户少使用同步调用。  
4. 通过short类型的serviceId和methodId定位被调用方法，可以大大减少数据传输量，而且定位方法更快。  

总的来说: Rpc设计更像多线程编程，以异步为主。

### 序列化
序列化的目标：（反）序列化速度要快， 序列化后的数据要小，尽量支持任意的数据结构，对应用程序尽可能少的限制。  
前两个目标主要使用了protoBuf的底层，倒不是太大的问题，真正的难题在于支持任意的数据结构，尤其是多态问题。  
反复的研究了JSON、BSON、ProtoBuf支持的数据结构，然后制定了自己的序列化格式和基本数据结构，值类型枚举为**BinaryValueType**。  
说一下大致原理：
1. 序列化时，放弃了字段的名字（JSON/BSON系），也放弃了字段的number(ProtoBuf系)，这样之后，所有的容器对象都是等价的，仅仅是拥有一组值的值。普通对象、数组、集合、map都是如此。
当然，这样放弃了一定的兼容性，这个在项目中实际影响不大。
2. 对于容器对象，在序列化时，需要编码它的类型信息。在该项目中，一个**TypeId**表示一个类型信息。如果一个类型信息关联的**TypeId**是唯一的，则可以精确解析。
3. 在序列化时，会将类型信息编码到数据中，如果存在关联的TypeId，使用其关联的TypeId；如果不存在，则尝试分配**默认的TypeId**,主要用于集合和数组，如果找不到合适的TypeId，则序列化失败。
4. 反序列化就不多说了，应该能想到了。
5. 多态问题：任意的集合/Map/数组的内容都是可序列化的，但其类型信息不一定是可序列化的。比如一些不可变集合，它们只能使用普通集合的**TypeId**序列化，因此默认
解码之后是**ArrayList**或**LinkedHashMap**或**LinkedHashSet**，那如果想解码为其它类型怎么办？ - 在解码时传入工厂，详见**ObjectReader**中的读取集合数组等方法。
6. 为方便使用，提供了**SerializableClass**、**SerializableField**以及**Impl**注解，注解处理器会自动生成对应的编解码处理器。在**example**包中有示例。  
表现: （反）序列化速度还是很快的；不必定义proto文件，支持任意的数据结构；可以通过注解为bean生成编解码类，也可以手动编解码（简单易用的编解码API）；可以更好的处理多态问题。

最后说下大对象序列化的问题，大对象的序列化开销始终还是挺大的，如果在逻辑层大规模序列化，会拖慢逻辑层性能，一般我们网络线程远多于逻辑线程，在网络层进行序列化，则可以分担逻辑层压力。
但是网络层序列化的话，也有危险，那就是可变对象，例如一个对象将自己持有的属性进行序列化，但是又没有进行保护性拷贝，这就很危险了。

### 日志搜集
日志搜集组件是**插件式**的，类似SLF和Log4J。应用基于log-api发布和消费日志，在启动时指定具体的实现和对应的适配组件。
默认提供了基于Kafka的日志发布和消费实现。

### GUID生成方案
Guid生成器中定义了命名空间的概念，只要求同一个命名空间下的id不重复。不同的业务可以使用不同的guid生成器，可以减少guid分配的分配压力。  
该组件也是插件式的，接口包为guid-api包，并提供了基于redis和zookeeper的实现。

### 基于Instrumentation的热更新机制
相对于其它热更新机制(自定义ClassLoader，继承等)，基于Instrumentation的热更新方法是最方便的，对开发影响也是最小的。  
其具体限制可以谷歌，经验是我们在IDE中能热更的代码，都可以在线上更新（因为是同一套API）。

### 数据存储
常见方案：增量更新，全量入库。  
增量更新的理论并不复杂，主要原理是代理。增量更新有其好处，但也有很多不友好的地方，甚至我觉得其缺点盖过优点，一直找不到好的解决方案。已决定放弃增量更新。
如果采取全量入库的话，则不需要单独的组件，因此删除db模块。

***
### 编译要求 jdk11
[jdk11百度云下载链接](https://pan.baidu.com/s/10IWbDpIeVDk5iPjci0gDUw)  提取码: d13j  
请下载 **Amazon** 11.0.4 或 11.0.7版本

### 如何使用注解处理器(编译出现找不到符号问题怎么解决)？
+ 方式1 - 自己打包安装：  
> 1. 将game-apt作为单独项目打开
> 2. install game-apt 到本地仓库
> 3. 在game-parent下clean，再compile，可消除缺少类文件的报错。

+ 方式2 - 安装已有jar包
> 1. 将game-libs下 game-apt.jar 安装安装到本地仓库。
> 2. 在game-parent下clean，再compile，可消除缺少类文件的报错。

***
### 序列化优化 2020/08/08
之前建立的抽象有点问题，最近又研究了一遍protoBuf和bson，修正了某些抽象。类数量减少，概念减少，支持却更强大了。  
另外，增强了对netty的ByteBuf的支持，可以利用其自动扩容特性了，分配合适的初始化容量的情况下，多数消息可以直接写入ByteBuf而不必扩容。
注意: 需要重新安装注解处理器。


### [历史重要更新](https://github.com/hl845740757/fastjgame/blob/master/%E5%8E%86%E5%8F%B2%E9%87%8D%E8%A6%81%E6%9B%B4%E6%96%B0.md)

(Markdown语法不是很熟悉，排版什么的后期有空再优化~)