package com.pujh.copy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class CopyableProcessor extends AbstractProcessor {
    private Messager mMessager;
    private Filer mFiler;
    private Elements mElementUtils;
    private Types mTypeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mMessager = processingEnv.getMessager();
        mFiler = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        mTypeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element copyableElement : roundEnv.getElementsAnnotatedWith(Copyable.class)) {
            if (copyableElement.getKind() != ElementKind.CLASS) { //必须声明在类上面
                mMessager.printMessage(Diagnostic.Kind.ERROR,
                        "@Copyable can only be applied to classes", copyableElement);
                return true;
            }

            ExecutableElement constructorElement = null;
            List<VariableElement> fieldElements = new ArrayList<>();
            List<ExecutableElement> methodElements = new ArrayList<>();

            //只允许有一个有参构造方法
            //构造方法所有参数能在原有类中找到对应的非静态非私有的属性或方法
            for (Element enclosedElement : copyableElement.getEnclosedElements()) {
                ElementKind kind = enclosedElement.getKind();
                if (kind == ElementKind.CONSTRUCTOR) {
                    if (constructorElement != null) { //如果有多个构造器
                        mMessager.printMessage(Diagnostic.Kind.ERROR,
                                "@Copyable can only be applied to classes", copyableElement);
                        return true;
                    }
                    constructorElement = (ExecutableElement) enclosedElement;
                } else if (kind == ElementKind.FIELD) {
                    Set<Modifier> modifiers = enclosedElement.getModifiers();
                    if (!modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.PRIVATE)) {
                        VariableElement fieldElement = (VariableElement) enclosedElement;
                        fieldElements.add(fieldElement);
                    }
                } else if (kind == ElementKind.METHOD) {
                    Set<Modifier> modifiers = enclosedElement.getModifiers();
                    if (!modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.PRIVATE)) {
                        ExecutableElement methodElement = (ExecutableElement) enclosedElement;
                        methodElements.add(methodElement);
                    }
                }
            }

            //必须有一个构造方法
            if (constructorElement == null) {
                mMessager.printMessage(Diagnostic.Kind.ERROR,
                        "@Copyable applied classes must has a constructor", copyableElement);
                return true;
            }

            List<? extends VariableElement> parameters = constructorElement.getParameters();
            //构造方法必须有参
            if (parameters.isEmpty()) {
                mMessager.printMessage(Diagnostic.Kind.ERROR,
                        "@Copyable applied classes constructor must has parameters", copyableElement);
                return true;
            }

            TypeElement typeElement = (TypeElement) copyableElement;
            String packageName = mElementUtils.getPackageOf(typeElement).toString();
            String className = typeElement.getSimpleName().toString();
            String copierClassName = className + "Copier";

            try {
                // 创建 copy 方法
                MethodSpec.Builder copyMethod = MethodSpec.methodBuilder("copy")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addParameter(ClassName.get(packageName, className), "old")
                        .returns(ClassName.get(packageName, className))
                        .beginControlFlow("if (old == null)")
                        .addStatement("return null")
                        .endControlFlow();

                // 创建 get 方法
                List<MethodSpec> getMethods = new ArrayList<>();
                StringBuilder parametersBuilder = new StringBuilder();

                for (int i = 0; i < parameters.size(); i++) {
                    VariableElement parameter = parameters.get(i);

                    TypeMirror parameterType = parameter.asType();
                    String parameterName = parameter.getSimpleName().toString();

                    Element methodOrFieldElement = findMethodOrFieldByParameter(parameter, methodElements, fieldElements, copyableElement);
                    if (methodOrFieldElement instanceof ExecutableElement) {
                        MethodSpec getMethod = generateGetMethodFromMethodElement(parameter, (ExecutableElement) methodOrFieldElement);
                        getMethods.add(getMethod);
                        copyMethod.addStatement("$T $L = $L(old.$L())", parameterType, parameterName, getMethod.name, methodOrFieldElement.getSimpleName().toString());
                    } else if (methodOrFieldElement instanceof VariableElement) {
                        MethodSpec getMethod = generateGetMethodFromFieldElement(parameter, (VariableElement) methodOrFieldElement);
                        getMethods.add(getMethod);
                        copyMethod.addStatement("$T $L = $L(old.$L)", parameterType, parameterName, getMethod.name, methodOrFieldElement.getSimpleName().toString());
                    } else {
                        return true;
                    }
                    if (i == 0) {
                        parametersBuilder.append(parameterName);
                    } else {
                        parametersBuilder.append(", ").append(parameterName);
                    }
                }

                copyMethod.addStatement("return new $T($L)", ClassName.get(packageName, className), parametersBuilder.toString())
                        .build();

                // 创建 Copier 类
                TypeSpec copierClass = TypeSpec.classBuilder(copierClassName)
                        .addSuperinterface(ParameterizedTypeName.get(ClassName.get(ICopier.class), ClassName.get(packageName, className)))
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addMethods(getMethods)
                        .addMethod(copyMethod.build())
                        .build();

                // 创建 Java 文件
                JavaFile javaFile = JavaFile.builder(packageName, copierClass).build();
                javaFile.writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private MethodSpec generateGetMethodFromMethodElement(VariableElement parameter, ExecutableElement methodElement) {
        TypeMirror parameterType = parameter.asType();
        String methodName = methodElement.getSimpleName().toString(); //生成类的方法与原类的方法名保持一致

        boolean isPrimitive = parameterType.getKind().isPrimitive();
        NonNull nonNull = parameter.getAnnotation(NonNull.class);

        return generateGetMethod(parameterType, methodName, isPrimitive, isPrimitive || nonNull != null);
    }

    private MethodSpec generateGetMethodFromFieldElement(VariableElement parameter, VariableElement fieldElement) {
        TypeMirror parameterType = parameter.asType();
        String fieldName = fieldElement.getSimpleName().toString();
        if (checkMStartString(fieldName)) { //如果以m开头，则去除m后，并小写首字母
            fieldName = lowercaseFirstLetter(fieldName.substring(1));
        }
        String methodName;
        if (parameterType.getKind() == TypeKind.BOOLEAN) {
            if (checkIsStartString(fieldName) || checkHasStartString(fieldName)) { //如果属性以isX...或hasX...开头，则生成同名方法
                methodName = fieldName;
            } else {
                methodName = "is" + uppercaseFirstLetter(fieldName);
            }
        } else {
            methodName = "get" + uppercaseFirstLetter(fieldName);
        }
        boolean isPrimitive = parameterType.getKind().isPrimitive();
        NonNull nonNull = parameter.getAnnotation(NonNull.class);

        return generateGetMethod(parameterType, methodName, isPrimitive, isPrimitive || nonNull != null);
    }

    private MethodSpec generateGetMethod(TypeMirror parameterType, String methodName, boolean isPrimitive, boolean nonNull) {
        if (isPrimitive) {
            return MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PROTECTED)
                    .addParameter(TypeName.get(parameterType), "oldValue")
                    .returns(TypeName.get(parameterType))
                    .addStatement("return oldValue")
                    .build();
        }
        if (nonNull) {
            return MethodSpec.methodBuilder(methodName)
                    .addAnnotation(NonNull.class)
                    .addModifiers(Modifier.PROTECTED)
                    .addParameter(ParameterSpec.builder(TypeName.get(parameterType), "oldValue")
                            .addAnnotation(NonNull.class)
                            .build())
                    .returns(TypeName.get(parameterType))
                    .addStatement("return oldValue")
                    .build();
        }
        return MethodSpec.methodBuilder(methodName)
                .addAnnotation(Nullable.class)
                .addModifiers(Modifier.PROTECTED)
                .addParameter(ParameterSpec.builder(TypeName.get(parameterType), "oldValue")
                        .addAnnotation(Nullable.class)
                        .build())
                .returns(TypeName.get(parameterType))
                .addStatement("return oldValue")
                .build();
    }

    private Element findMethodOrFieldByParameter(VariableElement parameter, List<ExecutableElement> methodElements, List<VariableElement> filedElements, Element copyableElement) {
        TypeMirror parameterType = parameter.asType();
        String parameterName = parameter.getSimpleName().toString();
        boolean isboolean = parameterType.getKind() == TypeKind.BOOLEAN;

        //先查找原先类中有没有对应get方法
        String firstMethodName;
        String secondMethodName;
        String thirdMethodName;
        if (isboolean) {
            if (checkIsStartString(parameterName)) {
                firstMethodName = parameterName;
                secondMethodName = "has" + parameterName.substring(2);
                thirdMethodName = "get" + parameterName.substring(2);
            } else if (checkHasStartString(parameterName)) { //hasX..这种格式
                firstMethodName = parameterName;
                secondMethodName = "is" + parameterName.substring(3);
                thirdMethodName = "get" + parameterName.substring(3);
            } else {
                firstMethodName = "is" + uppercaseFirstLetter(parameterName);
                secondMethodName = "has" + uppercaseFirstLetter(parameterName);
                thirdMethodName = "get" + uppercaseFirstLetter(parameterName);
            }
        } else {
            firstMethodName = "get" + uppercaseFirstLetter(parameterName);
            secondMethodName = null;
            thirdMethodName = null;
        }

        for (ExecutableElement element : methodElements) {
            if (!mTypeUtils.isSameType(parameterType, element.getReturnType())) { //返回类型不一致
                continue;
            }
            if (!element.getParameters().isEmpty()) { //函数有参
                continue;
            }
            String name = element.getSimpleName().toString();
            if (firstMethodName.equals(name) ||
                    (secondMethodName != null && secondMethodName.equals(name)) ||
                    (thirdMethodName != null && thirdMethodName.equals(name))
            ) {
                return element;
            }
        }

        String firstFiledName = parameterName; //优先查找与现有参数名字一致的属性
        String secondFiledName = "m" + uppercaseFirstLetter(parameterName);
        String thirdFiledName = null;
        String fourthFiledName = null;
        if (isboolean) {
            if (!checkIsStartString(parameterName) && !checkHasStartString(parameterName)) {
                thirdFiledName = "is" + uppercaseFirstLetter(parameterName);
                fourthFiledName = "has" + uppercaseFirstLetter(parameterName);
            }
        }

        for (VariableElement element : filedElements) {
            if (!mTypeUtils.isSameType(parameterType, element.asType())) { //属性类型不一致
                continue;
            }
            String name = element.getSimpleName().toString();
            if (firstFiledName.equals(name) ||
                    secondFiledName.equals(name) ||
                    (thirdFiledName != null && thirdFiledName.equals(name)) ||
                    (fourthFiledName != null && fourthFiledName.equals(name))
            ) {
                return element;
            }
        }

        //not find method or filed.
        StringBuilder methodNameString = new StringBuilder(firstMethodName);
        if (secondMethodName != null) {
            methodNameString.append('/').append(secondMethodName);
        }
        if (thirdMethodName != null) {
            methodNameString.append('/').append(thirdMethodName);
        }

        StringBuilder filedNameString = new StringBuilder(firstFiledName)
                .append('/').append(secondFiledName);
        if (thirdFiledName != null) {
            filedNameString.append('/').append(thirdFiledName);
        }
        if (fourthFiledName != null) {
            filedNameString.append('/').append(fourthFiledName);
        }

        mMessager.printMessage(Diagnostic.Kind.ERROR,
                "@Copyable classes not find " + methodNameString + " method, or " + filedNameString + " field", copyableElement);
        return null;
    }

    /**
     * 判断是不是 mXxx 这种格式
     */
    private boolean checkMStartString(String s) {
        return s.startsWith("m") && (s.length() >= 2 && Character.isUpperCase(s.charAt(1)));
    }

    /**
     * 判断是不是 isXxx 这种格式
     */
    private boolean checkIsStartString(String s) {
        return s.startsWith("is") && (s.length() >= 3 && Character.isUpperCase(s.charAt(2)));
    }

    /**
     * 判断是不是 hasXxx 这种格式
     */
    private boolean checkHasStartString(String s) {
        return s.startsWith("has") && (s.length() >= 4 && Character.isUpperCase(s.charAt(3)));
    }

    /**
     * 将字符串首字母大写
     */
    public static String uppercaseFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 将字符串首字母小写
     */
    public static String lowercaseFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new HashSet<>();
        set.add(Copyable.class.getCanonicalName());
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }
}