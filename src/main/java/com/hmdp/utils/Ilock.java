package com.hmdp.utils;

public interface Ilock {
    boolean trylock(long timeoutSec);

    void unlock();
}
