package com.fabriclj;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自动提取 Minecraft API 方法签名
 * 
 * 使用反射扫描指定的类，提取所有公共静态方法和实例方法，
 * 包括参数名（如果使用 Parchment mappings）和返回类型。
 * 
 * 输出格式：Markdown 和 JSON
 */
public class ApiExtractor {
    
    private static final String OUTPUT_DIR = "api_ref";
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: ApiExtractor <class1> [class2] ...");
            System.out.println("Example: ApiExtractor net.minecraft.world.item.enchantment.EnchantmentHelper");
            return;
        }
        
        // 创建输出目录
        Path outputDir = Paths.get(OUTPUT_DIR);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
            return;
        }
        
        // 提取每个类的 API
        for (String className : args) {
            try {
                extractClassApi(className, outputDir);
            } catch (Exception e) {
                System.err.println("Failed to extract API for " + className + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("API extraction completed. Output directory: " + outputDir.toAbsolutePath());
    }
    
    /**
     * 提取单个类的 API
     */
    private static void extractClassApi(String className, Path outputDir) throws Exception {
        try {
            Class<?> clazz = Class.forName(className);
            
            System.out.println("Extracting API for: " + className);
            
            // 收集所有方法
            List<MethodInfo> methods = new ArrayList<>();
            
            // 公共静态方法
            for (Method method : clazz.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) 
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    methods.add(extractMethodInfo(method, true));
                }
            }
            
            // 公共实例方法
            for (Method method : clazz.getMethods()) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers())
                    && !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    && method.getDeclaringClass() == clazz) {
                    methods.add(extractMethodInfo(method, false));
                }
            }
            
            // 按方法名排序
            methods.sort(Comparator.comparing(m -> m.name));
            
            // 生成文件名（使用简单类名）
            String simpleName = clazz.getSimpleName();
            String fileName = simpleName + ".md";
            String jsonFileName = simpleName + ".json";
            
            // 生成 Markdown
            generateMarkdown(clazz, methods, outputDir.resolve(fileName));
            
            // 生成 JSON
            generateJson(clazz, methods, outputDir.resolve(jsonFileName));
            
            System.out.println("  Extracted " + methods.size() + " methods");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            System.err.println("  SKIPPED: " + className + " (requires Minecraft bootstrap)");
            System.err.println("    Reason: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("  FAILED: " + className);
            System.err.println("    Error: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 提取方法信息
     */
    private static MethodInfo extractMethodInfo(Method method, boolean isStatic) {
        MethodInfo info = new MethodInfo();
        info.name = method.getName();
        info.isStatic = isStatic;
        info.returnType = formatType(method.getReturnType());
        info.returnTypeGeneric = formatGenericType(method.getGenericReturnType());
        
        // 提取参数
        Parameter[] params = method.getParameters();
        info.parameters = new ArrayList<>();
        for (Parameter param : params) {
            ParamInfo paramInfo = new ParamInfo();
            paramInfo.name = param.getName();
            paramInfo.type = formatType(param.getType());
            paramInfo.typeGeneric = formatGenericType(param.getParameterizedType());
            info.parameters.add(paramInfo);
        }
        
        // 提取异常
        Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length > 0) {
            info.exceptions = Arrays.stream(exceptions)
                .map(ApiExtractor::formatType)
                .collect(Collectors.toList());
        }
        
        return info;
    }
    
    /**
     * 格式化类型名称
     */
    private static String formatType(Class<?> type) {
        if (type.isPrimitive()) {
            return type.getName();
        }
        if (type.isArray()) {
            return formatType(type.getComponentType()) + "[]";
        }
        return type.getName();
    }
    
    /**
     * 格式化泛型类型
     */
    private static String formatGenericType(java.lang.reflect.Type type) {
        if (type instanceof Class) {
            return formatType((Class<?>) type);
        }
        return type.getTypeName();
    }
    
    /**
     * 生成 Markdown 文档
     */
    private static void generateMarkdown(Class<?> clazz, List<MethodInfo> methods, Path outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            writer.write("# " + clazz.getName() + "\n\n");
            writer.write("**Package:** `" + clazz.getPackage().getName() + "`\n\n");
            writer.write("**Extracted:** " + new Date() + "\n\n");
            writer.write("---\n\n");
            
            // 按静态/实例分组
            List<MethodInfo> staticMethods = methods.stream()
                .filter(m -> m.isStatic)
                .collect(Collectors.toList());
            List<MethodInfo> instanceMethods = methods.stream()
                .filter(m -> !m.isStatic)
                .collect(Collectors.toList());
            
            if (!staticMethods.isEmpty()) {
                writer.write("## Static Methods\n\n");
                for (MethodInfo method : staticMethods) {
                    writeMethodMarkdown(writer, method);
                }
                writer.write("\n");
            }
            
            if (!instanceMethods.isEmpty()) {
                writer.write("## Instance Methods\n\n");
                for (MethodInfo method : instanceMethods) {
                    writeMethodMarkdown(writer, method);
                }
            }
        }
    }
    
    /**
     * 写入单个方法的 Markdown
     */
    private static void writeMethodMarkdown(FileWriter writer, MethodInfo method) throws IOException {
        writer.write("### `" + method.name + "`\n\n");
        
        // 方法签名
        StringBuilder signature = new StringBuilder();
        signature.append(method.isStatic ? "static " : "");
        signature.append(method.returnTypeGeneric).append(" ");
        signature.append(method.name).append("(");
        
        if (!method.parameters.isEmpty()) {
            for (int i = 0; i < method.parameters.size(); i++) {
                if (i > 0) signature.append(", ");
                ParamInfo param = method.parameters.get(i);
                signature.append(param.typeGeneric).append(" ").append(param.name);
            }
        }
        signature.append(")");
        
        if (method.exceptions != null && !method.exceptions.isEmpty()) {
            signature.append(" throws ");
            signature.append(String.join(", ", method.exceptions));
        }
        
        writer.write("```java\n");
        writer.write(signature.toString());
        writer.write("\n```\n\n");
        
        // 参数列表
        if (!method.parameters.isEmpty()) {
            writer.write("**Parameters:**\n");
            for (ParamInfo param : method.parameters) {
                writer.write("- `" + param.name + "` (" + param.typeGeneric + ")\n");
            }
            writer.write("\n");
        }
        
        // 返回类型
        writer.write("**Returns:** `" + method.returnTypeGeneric + "`\n\n");
        
        writer.write("---\n\n");
    }
    
    /**
     * 生成 JSON 文档
     */
    private static void generateJson(Class<?> clazz, List<MethodInfo> methods, Path outputFile) throws IOException {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("className", clazz.getName());
        json.put("package", clazz.getPackage().getName());
        json.put("extracted", new Date().toString());
        json.put("methodCount", methods.size());
        
        List<Map<String, Object>> methodsJson = new ArrayList<>();
        for (MethodInfo method : methods) {
            Map<String, Object> methodJson = new LinkedHashMap<>();
            methodJson.put("name", method.name);
            methodJson.put("static", method.isStatic);
            methodJson.put("returnType", method.returnType);
            methodJson.put("returnTypeGeneric", method.returnTypeGeneric);
            
            List<Map<String, String>> paramsJson = new ArrayList<>();
            for (ParamInfo param : method.parameters) {
                Map<String, String> paramJson = new LinkedHashMap<>();
                paramJson.put("name", param.name);
                paramJson.put("type", param.type);
                paramJson.put("typeGeneric", param.typeGeneric);
                paramsJson.add(paramJson);
            }
            methodJson.put("parameters", paramsJson);
            
            if (method.exceptions != null) {
                methodJson.put("exceptions", method.exceptions);
            }
            
            methodsJson.add(methodJson);
        }
        json.put("methods", methodsJson);
        
        // 简单的 JSON 序列化（不使用外部库）
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            writer.write(formatJson(json, 0));
        }
    }
    
    /**
     * 简单的 JSON 格式化（不使用外部库）
     */
    private static String formatJson(Object obj, int indent) {
        String indentStr = "  ".repeat(indent);
        String nextIndent = "  ".repeat(indent + 1);
        
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(nextIndent).append("\"").append(entry.getKey()).append("\": ");
                sb.append(formatJson(entry.getValue(), indent + 1));
            }
            sb.append("\n").append(indentStr).append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[\n");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(nextIndent).append(formatJson(item, indent + 1));
            }
            sb.append("\n").append(indentStr).append("]");
            return sb.toString();
        } else if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        } else {
            return "\"" + escapeJson(String.valueOf(obj)) + "\"";
        }
    }
    
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    // 内部类：方法信息
    static class MethodInfo {
        String name;
        boolean isStatic;
        String returnType;
        String returnTypeGeneric;
        List<ParamInfo> parameters = new ArrayList<>();
        List<String> exceptions;
    }
    
    // 内部类：参数信息
    static class ParamInfo {
        String name;
        String type;
        String typeGeneric;
    }
}
