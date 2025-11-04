
cmake_minimum_required(VERSION 3.22.1)


project("clip-android")

add_subdirectory(../../../../../../ build-clip)

add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    clip_android.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        main_lib
        clip
        ggml
        android
        log)
