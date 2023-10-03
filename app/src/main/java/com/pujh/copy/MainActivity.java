package com.pujh.copy;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Student student = new Student("张三", 18, "四川省成都市", true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView textView = findViewById(R.id.textView);
        textView.setText(student.toString());

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //同时修改name和age属性。
                student = new StudentCopier() {
                    @Override
                    protected String getName(String oldValue) {
                        return "李四:" + System.currentTimeMillis();
                    }

                    @Override
                    protected int getAge(int oldValue) {
                        return oldValue + 1;
                    }
                }.copy(student);
                textView.setText(student.toString());
            }
        });
    }
}