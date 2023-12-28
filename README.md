# JavaCopy
使用注解处理器来帮助java实现kotlin的data class copy的功能

[![JitPack](https://jitpack.io/v/pujunhui/JavaCopy.svg)](https://jitpack.io/#pujunhui/JavaCopy)

工作原理如下：
1. 注解处理器会去解析所有使用`@Copyable`注解的类。
2. 解析构造方法，获得创建这个对象所需要的参数。
3. 根据参数名称和类型，从这个类中找到对应的get方法或属性。
4. 生成Copier类，对构造方法中的每个参数生成对应的get方法。
5. 生成copy方法，从原对象中获取旧值，传入Copier类对应的get方法，然后调用构造方法，返回新的对象。

* 注意：使用@Copyable注解的类，只允许有且只有一个有参构造方法。

其中查找规则如下：
1. 优先查找对应名称和返回类型相同的非private的get方法。
2. 然后再查找对应名称和类型相同的非private的属性。

get方法查找规则如下：
1. 如果非boolean值，则直接查找对应的get方法。
2. 如果是boolean值，且名称以has开头，则依次查找has/is/get方法，否则依次查找has/is/get方法。

属性查找规则如下：
1. 先查找名称相同的属性。
2. 再查找以m开头的属性。
3. 如果是boolean值，且参数名称不是以is/has开头，则再查找以is/has开头的属性


比如以下Student类：
``` java
@Copyable
public final class Student {
    private final String name;
    public final int age;
    public final String mAddress;
    private final boolean isBoy;

    /**
     *
     * @param name 通过name能够找到getName()方法。
     * @param age 通过age能够找到this.age属性。
     * @param address 通过address能够找到this.mAddress属性。
     * @param boy 通过boy能够找到isBoy()方法。
     */
    public Student(String name, int age, String address, boolean boy) {
        this.name = name;
        this.age = age;
        mAddress = address;
        this.isBoy = boy;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public boolean isBoy() {
        return isBoy;
    }

    @NonNull
    @Override
    public String toString() {
        return "Student{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", mAddress='" + mAddress + '\'' +
                ", isBoy=" + isBoy +
                '}';
    }
}
```

会生成StudentCopier类：
``` java
public abstract class StudentCopier implements ICopier<Student> {
    protected String getName(String oldValue) {
        return oldValue;
    }

    protected int getAge(int oldValue) {
        return oldValue;
    }

    protected String getDdress(String oldValue) {
        return oldValue;
    }

    protected boolean isBoy(boolean oldValue) {
        return oldValue;
    }

    @Override
    public final Student copy(Student old) {
        if (old == null) {
            return null;
        }
        String name = getName(old.getName());
        int age = getAge(old.getAge());
        String address = getDdress(old.mAddress);
        boolean boy = isBoy(old.isBoy());
        return new Student(name, age, address, boy);
    }
}
```

使用方法如下：
``` java
Student oldStudent = new Student("张三", 18, "四川省成都市", true);
Student newStudent = new StudentCopier() {
    @Override
    protected String getName(String oldValue) {
        return "李四:" + System.currentTimeMillis();
    }

    @Override
    protected int getAge(int oldValue) {
        return oldValue + 1;
    }
}.copy(oldStudent);
```

gradle使用:

``` gradle
dependencies {
    implementation "com.github.pujunhui.JavaCopy:annotation:1.0.2"
    annotationProcessor "com.github.pujunhui.JavaCopy:processer:1.0.2"
}
```