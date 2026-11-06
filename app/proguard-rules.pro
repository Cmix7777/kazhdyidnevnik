# Сжатие без переименования: стеки в отчёте о вылете остаются читаемыми, а WorkManager находит
# свои задачи по именам классов, записанным ещё прежней версией.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# kotlinx.serialization: сериализаторы ищутся через Companion.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Необязательные зависимости jsoup и прочего, которых нет в приложении.
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
