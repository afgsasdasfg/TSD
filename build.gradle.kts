buildscript {
    val agp_version by extra("8.6.0")
}
plugins {
    id("com.android.application") version "8.6.0" apply false
    kotlin("android") version "1.9.25" apply false  // ← ДОЛЖНА быть 1.9.x для compose 1.5.15
}