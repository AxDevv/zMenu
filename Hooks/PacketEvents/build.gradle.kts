group = "Hooks:PacketEvents"

dependencies {
    compileOnly(projects.common)
    compileOnly(libs.packetevents)
    testImplementation(libs.packetevents)
    testImplementation(libs.paper.api)
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("io.netty:netty-buffer:4.1.97.Final")
}
