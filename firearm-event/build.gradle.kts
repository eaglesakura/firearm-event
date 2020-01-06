apply(from = "../dsl/android-library.gradle")
apply(from = "../dsl/ktlint.gradle")
apply(from = "../dsl/bintray.gradle")

dependencies {
    "implementation"("androidx.multidex:multidex:2.0.1")
    "implementation"("androidx.arch.core:core-common:2.1.0")
    "implementation"("androidx.arch.core:core-runtime:2.1.0")
    "implementation"("androidx.core:core:1.1.0")
    "implementation"("androidx.core:core-ktx:1.1.0")
    "implementation"("androidx.collection:collection:1.1.0")
    "implementation"("androidx.collection:collection-ktx:1.1.0")
    "implementation"("androidx.fragment:fragment:1.2.0-rc04")
    "implementation"("androidx.fragment:fragment-ktx:1.2.0-rc04")
    "implementation"("androidx.lifecycle:lifecycle-extensions:2.1.0")
    "implementation"("androidx.lifecycle:lifecycle-viewmodel:2.1.0")
    "implementation"("androidx.lifecycle:lifecycle-viewmodel-savedstate:1.0.0-rc03")
    "implementation"("androidx.lifecycle:lifecycle-viewmodel-ktx:2.1.0")
    "implementation"("androidx.lifecycle:lifecycle-runtime:2.1.0")
    "implementation"("androidx.lifecycle:lifecycle-common-java8:2.1.0")
    "implementation"("androidx.lifecycle:lifecycle-reactivestreams:2.1.0")
    "implementation"("androidx.lifecycle:lifecycle-reactivestreams-ktx:2.1.0")

    "implementation"("com.eaglesakura.armyknife.armyknife-jetpack:armyknife-jetpack:1.4.3")
    "api"("com.eaglesakura.armyknife.armyknife-reactivex:armyknife-reactivex:1.3.0") {
        exclude(group = "com.eaglesakura.armyknife.armyknife-jetpack")
    }

}