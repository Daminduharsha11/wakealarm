cmake_minimum_required(VERSION 3.22.1)
project("rtc_setter")

add_library(rtc_setter SHARED rtc_setter.c)

# Force 16 KB page-size alignment for Android 15+ compatibility
target_link_options(rtc_setter PRIVATE "-Wl,-z,max-page-size=16384")