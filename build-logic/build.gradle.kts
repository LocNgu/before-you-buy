plugins {
    `kotlin-dsl`
}

dependencies {
    // AGP's public API only; the app build supplies the implementation at runtime.
    compileOnly(libs.android.gradle.api)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("noInternetPermission") {
            id = "beforeyoubuy.no-internet-permission"
            implementationClass = "io.github.locngu.beforeyoubuy.buildlogic.NoInternetPermissionPlugin"
        }
        register("stringParity") {
            id = "beforeyoubuy.string-parity"
            implementationClass = "io.github.locngu.beforeyoubuy.buildlogic.StringParityPlugin"
        }
    }
}
