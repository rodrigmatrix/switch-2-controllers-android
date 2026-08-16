# Consumer Proguard Rules for switch-2-controllers-android
-keep class com.switch2.controllers.** { *; }
-keepclassmembers class com.switch2.controllers.bridge.Switch2WebViewBridge {
    @android.webkit.JavascriptInterface <methods>;
}
