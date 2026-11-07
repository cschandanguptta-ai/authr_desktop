plugins {
    // Work around the foojay resolver being unavailable in some sandboxes:
    // we pin the JDK to the locally installed JDK 21 via toolchain management below.
}

tasks.register("printVersion") {
    doLast { println("Authr Desktop - Compose Multiplatform port of Authr v2.3") }
}
