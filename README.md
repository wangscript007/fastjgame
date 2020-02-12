### fastjgame
fastjgame 为 fast java game framework的缩写，如名字一样，该项目的目标是一个高性能，高稳定性的游戏服务器架构。  

### 暂不建议fork，欢迎star和watch
加上个人经验及能力原因，代码改动频繁，甚至很多设计都会推翻，因此暂时不建议fork。

### 2020年2月12日 超大型修改(重组)
从创建仓库到现在已过去9个月，写了不少东西，但是到最近才渐渐明白，之前写的东西，不能叫架构，只能一套能运行的程序，
其中犯了一个大错误，**架构应只与业务相关，应该与框架无关**，而我的实现处处与框架强依赖。  

如果我自己评价之前的我的话，可以表述为：**有点项目经验，能力还不错，但是根本不懂架构设计**。  
为什么这么说呢？  
对于我经历过的项目，底层都看的很明白，用到的很多技术都懂一些；  
JAVA语言基础、设计模式、重构这些基本功都很扎实，并发、网络编程也还不错；  
深入理解过的框架有netty和disruptor，会用的框架和工具包括mongodb、redis、kafka、OkHttp等；  
游戏内的一些技术，如：怪物AI，技能，寻路，视野管理等也会;  
在我以前的认知里，把这些拼起来，能运行起来，就是所谓的架构，这是**大错特错**。  

举些栗子：
1. 我用过redis，知道jedis，基于jedis封装一套异步api，为什么要用redis？非用redis不可吗？
2. 我用过kafka，基于kafka写了一套将日志发布到kafka，和从kafka拉取日志消费的实现，非用kafka不可吗？
3. 我用过OkHttp，基于OkHttp写了一套Http请求api，非用OkHttp不可吗？
4. 我用过MongoDB，基于MongoDB写了一套数据增删改查的api，但是非用MongoDB不可吗？

对于以上问题，答案当前全为**否定的**，因为我有这个锤子，我就得把这个锤子用起来吗？当然不能。之前写的东西总结来说就是：
**都是针对锤子(框架/工具)写的，而不是针对业务写的**，导致**不但业务不明确，且强依赖于框架**，此外还存在一些别的问题，
都表示了我并不懂架构设计，虽然我在写业务逻辑的时候代码组织还不错，但在宏观/高层架构能力不足。现在想一想，我之前项目的某些设计要好过我项目的设计。

**强依赖于框架会存在什么问题？**
1. 框架(工具)会限制你的业务。  
   假设之前的业务你都是依赖redis实现的，现在有个需求，但redis不支持，你就会给策划说，**做不了**！
这样的设计会导致你基于工具去考虑业务，而不是基于业务区选择工具。
2. 更换框架(工具)成本过高，甚至无法更换。  
   假设之前的http请求你都是使用Apache HttpClient实现的，现在某些原因导致希望能同时支持同步请求和异步请求以提升性能，
但是由于大量的地方直接使用了Apache HttpClient相关类，导致切换成本太高，很难保证安全的切换。

通过不断的学习以及该项目的经验，最近我才从这个思维中跳出来。架构设计这块，知识很丰富，我给自己现在架构能力评价的话：入门。
**game-log-api**是我入门后第一个产物，它和以前有什么区别？
1. game-log-api是针对日志搜集与日志消费**业务的一套抽象**，它能完整表述整个流程，看完这套api我们就知道大概做了什么。
2. 不依赖具体的框架(工具)。
3. 不涉及外部细节，如：存在哪，如何存，如何取。
4. 细节的处理是作为**插件**的形式实现的，**game-log-kafka**是一个基于kafka实现的插件，如果想存储在MySql中，那只需要针对MySql写一个插件，
对业务逻辑不造成任何影响。

我其实一直不想自己的项目像你在github或其它地方看见的说自己多么多么流弊，用了哪些哪些技术，但实际代码烂的一塌糊涂的所谓服务器框架一样，
但是我的项目最终也是在慢慢腐烂，也变得类似。  
我还是看过不少项目的，多数人基本功都很差，细节代码的设计乱七八糟，写多线程框架的，多数并发基础都不扎实，稍微看几个类就能发现并发错误，对于架构上的设计，我以前能力也不足，因此没有太多印象。
我自己项目腐烂的主要原因是架构能力的不足。

最后，那么本次更新主要做了些什么呢？
1. 项目重组，本项目只保留底层架构部分，MMO相关模块新建仓库(fastjgame-mmo)，暂时为private，还未开放。  
2. 把用到了什么什么技术啊，这些具体的细节信息从项目介绍中删除了，那不属于架构。

***
### 项目里我要写什么？
能复用的底层技术，目前我总结包括：
1. 线程模型。
2. 网络层(rpc、http)
3. 数据存储
4. 游戏日志搜集
5. 服务器发现
6. guid(唯一id)生成方案

***
### 如何使用注解处理器(编译出现找不到符号问题怎么解决)？
+ 方式1 - 自己打包安装：  
> 1. 将game-apt作为单独项目打开
> 2. install game-apt 到本地仓库
> 3. 在game-parent下clean，再compile，可消除缺少类文件的报错。

+ 方式2 - 安装已有jar包
> 1. 将game-libs下 game-apt.jar 安装安装到本地仓库。
> 2. 在game-parent下clean，再compile，可消除缺少类文件的报错。

***
### [历史重要更新](https://github.com/hl845740757/fastjgame/blob/master/%E5%8E%86%E5%8F%B2%E9%87%8D%E8%A6%81%E6%9B%B4%E6%96%B0.md)

(Markdown语法不是很熟悉，排版什么的后期有空再优化~)
