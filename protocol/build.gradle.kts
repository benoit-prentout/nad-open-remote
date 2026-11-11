plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test")) }
tasks.test { jvmArgs("-Djava.net.preferIPv4Stack=true") }
