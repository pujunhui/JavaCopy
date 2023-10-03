package com.pujh.copy;

import androidx.annotation.NonNull;

/**
 * 只允许有且只有一个有参构造方法，通过参数名称，在类中查找get方法或属性
 * 1、在类中查找对应名称和返回类型相同的非private的get方法
 * 2、在类在查找对应名称和类型相同的非private的属性
 * 其中名称匹配规则如下：
 * 如果非boolean值，则查找其get方法。
 * 如果是boolean值：如果名称以is开头，则依次查找is/has/get方法
 * 如果是boolean值：如果名称以has开头，则依次查找has/is/get方法
 * <p>
 * 如果方法找不到，再查找对应的属性：
 * 1、先查找名称相同的属性。
 * 2、再查找以m开头的属性。
 * 3、如果是boolean值，且参数名称不是以is/has开头，则再查找以is/has开头的属性
 */
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
