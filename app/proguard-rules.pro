# 表情摆渡 混淆规则
# 自用工具，保持默认即可。如需上架再细调。

# 保留 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.stickerferry.** {
    *** Companion;
}
-keepclasseswithmembers class com.stickerferry.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
