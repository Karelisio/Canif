# PdfBox-Android: optional JPX/JBIG2 decoders are not bundled
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
# Glance / widgets receivers are referenced from the manifest (kept by AAPT rules)
