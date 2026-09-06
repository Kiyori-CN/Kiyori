plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
