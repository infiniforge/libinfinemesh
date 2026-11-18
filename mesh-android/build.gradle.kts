plugins { id("com.android.library") version "8.9.2" }
android {
    namespace = "io.github.gretchycat.infinemesh.android"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(project(":mesh-core"))
    testImplementation("junit:junit:4.13.2")
}
