cmake_minimum_required(VERSION 3.10.2)
project("native-blur")

add_library(native-blur SHARED blur.cpp)

target_link_libraries(native-blur
    android
    log
    jnigraphics)
