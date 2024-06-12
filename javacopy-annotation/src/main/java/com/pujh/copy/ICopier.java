package com.pujh.copy;

public interface ICopier<T> {
    T copy(T old);
}
