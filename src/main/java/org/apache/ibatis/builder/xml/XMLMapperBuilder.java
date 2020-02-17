/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
public class XMLMapperBuilder extends BaseBuilder {

    private final XPathParser parser;
    private final MapperBuilderAssistant builderAssistant;
    private final Map<String, XNode> sqlFragments;
    private final String resource;

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(reader, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(inputStream, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        super(configuration);
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
        this.parser = parser;
        this.sqlFragments = sqlFragments;
        this.resource = resource;
    }

    /**
     * 解析 mapper 节点
     * 通过命名空间绑定 Mapper 接口
     * 处理未完成解析的节点
     */
    public void parse() {
        // 检测映射文件是否已经被解析过
        if (!configuration.isResourceLoaded(resource)) {
            // 解析 mapper 节点
            configurationElement(parser.evalNode("/mapper"));
            // 添加资源路径到“已解析资源集合”中
            configuration.addLoadedResource(resource);
            // 通过命名空间绑定 Mapper 接口
            bindMapperForNamespace();
        }
        // 处理未完成解析的节点
        parsePendingResultMaps();
        parsePendingCacheRefs();
        parsePendingStatements();
    }

    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }

    private void configurationElement(XNode context) {
        try {
            // 获取 mapper 命名空间
            String namespace = context.getStringAttribute("namespace");
            if (namespace == null || namespace.equals("")) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }

            // 设置命名空间到 builderAssistant 中
            builderAssistant.setCurrentNamespace(namespace);

            // 解析 <cache-ref> 节点
            cacheRefElement(context.evalNode("cache-ref"));

            // 解析 <cache> 节点
            cacheElement(context.evalNode("cache"));

            // 已废弃配置，这里不做分析
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));

            // 解析 <resultMap> 节点
            resultMapElements(context.evalNodes("/mapper/resultMap"));

            // 解析 <sql> 节点
            sqlElement(context.evalNodes("/mapper/sql"));

            // 解析 <select>、...、<delete> 等节点
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
        }
    }

    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }

    private void parsePendingResultMaps() {
        Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
        synchronized (incompleteResultMaps) {
            Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolve();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // ResultMap is still missing a resource...
                }
            }
        }
    }

    private void parsePendingCacheRefs() {
        Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
        synchronized (incompleteCacheRefs) {
            Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolveCacheRef();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Cache ref is still missing a resource...
                }
            }
        }
    }

    private void parsePendingStatements() {
        Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
        synchronized (incompleteStatements) {
            Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().parseStatementNode();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Statement is still missing a resource...
                }
            }
        }
    }

    private void cacheRefElement(XNode context) {
        if (context != null) {
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
            // 创建 CacheRefResolver 实例
            CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
            try {
                // 解析参照缓存
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                /*
                 * 这里对 IncompleteElementException 异常进行捕捉，并将 cacheRefResolver
                 * 存入到 Configuration 的 incompleteCacheRefs 集合中
                 */
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    private void cacheElement(XNode context) throws Exception {
        if (context != null) {
            // 获取各种属性
            // 判空操作1，若用户未设置 cache 节点的 type ，这里设置默认值 PERPETUAL
            String type = context.getStringAttribute("type", "PERPETUAL");
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);

            // 判空操作1，若用户未设置 cache 节点的 eviction 属性，这里设置默认值 LRU
            String eviction = context.getStringAttribute("eviction", "LRU");
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);

            Long flushInterval = context.getLongAttribute("flushInterval");
            Integer size = context.getIntAttribute("size");
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);
            boolean blocking = context.getBooleanAttribute("blocking", false);

            // 获取子节点配置
            Properties props = context.getChildrenAsProperties();

            // 构建缓存对象
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
        }
    }

    private void parameterMapElement(List<XNode> list) throws Exception {
        for (XNode parameterMapNode : list) {
            String id = parameterMapNode.getStringAttribute("id");
            String type = parameterMapNode.getStringAttribute("type");
            Class<?> parameterClass = resolveClass(type);
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
            for (XNode parameterNode : parameterNodes) {
                String property = parameterNode.getStringAttribute("property");
                String javaType = parameterNode.getStringAttribute("javaType");
                String jdbcType = parameterNode.getStringAttribute("jdbcType");
                String resultMap = parameterNode.getStringAttribute("resultMap");
                String mode = parameterNode.getStringAttribute("mode");
                String typeHandler = parameterNode.getStringAttribute("typeHandler");
                Integer numericScale = parameterNode.getIntAttribute("numericScale");
                ParameterMode modeEnum = resolveParameterMode(mode);
                Class<?> javaTypeClass = resolveClass(javaType);
                JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
                @SuppressWarnings("unchecked")
                Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    /**
     * 解析<resultMap></resultMap>
     *
     * @param list
     * @throws Exception
     */
    private void resultMapElements(List<XNode> list) throws Exception {
        // 遍历 <resultMap> 节点列表
        for (XNode resultMapNode : list) {
            try {
                // 解析 resultMap 节点
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
        // 调用重载方法
        return resultMapElement(resultMapNode, Collections.<ResultMapping>emptyList());
    }

    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());

        // 获取 id 和 type 属性
        String id = resultMapNode.getStringAttribute("id",
                resultMapNode.getValueBasedIdentifier());
        String type = resultMapNode.getStringAttribute("type",
                resultMapNode.getStringAttribute("ofType",
                        resultMapNode.getStringAttribute("resultType",
                                resultMapNode.getStringAttribute("javaType"))));

        // 获取 extends 和 autoMapping
        String extend = resultMapNode.getStringAttribute("extends");
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");

        // 解析 type 属性对应的类型
        Class<?> typeClass = resolveClass(type);
        Discriminator discriminator = null;
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        resultMappings.addAll(additionalResultMappings);

        // 获取并遍历 <resultMap> 的子节点列表
        List<XNode> resultChildren = resultMapNode.getChildren();
        /**
         * 循环resultMap里面的三个子节点
         *    <resultMap id="userResult" type="user">
         *         <id property="id" column="id"/>
         *         <result property="userName" column="user_name" javaType=""/>
         *         <result property="userPassword" column="user_password" javaType=""/>
         *         <!-- ... -->
         *     </resultMap>
         */
        for (XNode resultChild : resultChildren) {
            if ("constructor".equals(resultChild.getName())) {
                // 解析 constructor 节点，并生成相应的 ResultMapping
                processConstructorElement(resultChild, typeClass, resultMappings);
            } else if ("discriminator".equals(resultChild.getName())) {
                // 解析 discriminator 节点
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            } else {
                List<ResultFlag> flags = new ArrayList<ResultFlag>();
                if ("id".equals(resultChild.getName())) {
                    // 添加 ID 到 flags 集合中
                    flags.add(ResultFlag.ID);
                }
                // 解析 id 和 property 节点，并生成相应的 ResultMapping
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            // 根据前面获取到的信息构建 ResultMap 对象
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            /*
             * 如果发生 IncompleteElementException 异常，
             * 这里将 resultMapResolver 添加到 incompleteResultMaps 集合中
             */
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    /**
     * 该节点是resultMap 节点下的
     * <resultMap id="authorResult" type="author">
     *         <constructor>
     *             <idArg column="id" javaType="int"/>
     *             <arg column="name" javaType="string"/>
     *             <arg column="age" javaType="int"/>
     *         </constructor>
     *         <id property="id" column="id"/>
     *         <result property="name" column="name" />
     *         <result property="age" column="age"/>
     *     </resultMap>
     * @param resultChild
     * @param resultType
     * @param resultMappings
     * @throws Exception
     */
    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        // 获取子节点列表
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            List<ResultFlag> flags = new ArrayList<ResultFlag>();
            // 向 flags 中添加 CONSTRUCTOR 标志
            flags.add(ResultFlag.CONSTRUCTOR);
            if ("idArg".equals(argChild.getName())) {
                // 向 flags 中添加 ID 标志
                flags.add(ResultFlag.ID);
            }
            // 构建 ResultMapping，上一节已经分析过
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Map<String, String> discriminatorMap = new HashMap<String, String>();
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
            discriminatorMap.put(value, resultMap);
        }
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    private void sqlElement(List<XNode> list) throws Exception {
        if (configuration.getDatabaseId() != null) {
            sqlElement(list, configuration.getDatabaseId());
        }
        sqlElement(list, null);
    }

    private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
        for (XNode context : list) {
            String databaseId = context.getStringAttribute("databaseId");
            String id = context.getStringAttribute("id");
            id = builderAssistant.applyCurrentNamespace(id, false);
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                sqlFragments.put(id, context);
            }
        }
    }

    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            if (!requiredDatabaseId.equals(databaseId)) {
                return false;
            }
        } else {
            if (databaseId != null) {
                return false;
            }
            // skip this fragment if there is a previous one with a not null databaseId
            if (this.sqlFragments.containsKey(id)) {
                XNode context = this.sqlFragments.get(id);
                if (context.getStringAttribute("databaseId") != null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 根据resultMap每个子节点的xml字符设置
     * 解析 id 和 property 节点，并生成相应的 ResultMapping对象
     * @param context 子节点内容
     * @param resultType 解析resultMap 的type属性对应的类型，如User.class
     * @param flags
     * @return
     * @throws Exception
     */
    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
        String property;
        // 根据节点类型获取 name 或 property 属性
        if (flags.contains(ResultFlag.CONSTRUCTOR)) {
            property = context.getStringAttribute("name");
        } else {
            property = context.getStringAttribute("property");
        }

        // 获取其他各种属性
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String nestedSelect = context.getStringAttribute("select");

        /*
         * 解析 resultMap 属性，该属性出现在 <association> 和 <collection> 节点中。
         * 若这两个节点不包含 resultMap 属性，则调用 processNestedResultMappings 方法
         * 解析嵌套 resultMap。
         */
        String nestedResultMap = context.getStringAttribute("resultMap",
                processNestedResultMappings(context, Collections.<ResultMapping>emptyList()));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        String resultSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));

        // 解析 javaType、typeHandler 的类型以及枚举类型 JdbcType
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        // 构建 ResultMapping 对象
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
    }

    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
        // 判断节点名称
        if ("association".equals(context.getName())
                || "collection".equals(context.getName())
                || "case".equals(context.getName())) {
            if (context.getStringAttribute("select") == null) {
                // resultMapElement 是解析 ResultMap 入口方法
                ResultMap resultMap = resultMapElement(context, resultMappings);
                // 返回 resultMap id
                return resultMap.getId();
            }
        }
        return null;
    }

    private void bindMapperForNamespace() {
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
                //ignore, bound type is not required
            }
            if (boundType != null) {
                if (!configuration.hasMapper(boundType)) {
                    // Spring may not know the real resource name so we set a flag
                    // to prevent loading again this resource from the mapper interface
                    // look at MapperAnnotationBuilder#loadXmlResource
                    configuration.addLoadedResource("namespace:" + namespace);
                    configuration.addMapper(boundType);
                }
            }
        }
    }

}
