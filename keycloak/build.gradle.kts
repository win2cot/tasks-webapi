import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    id("net.ltgt.errorprone") version "5.1.0"
    id("com.diffplug.spotless") version "8.8.0"
}

group = "xyz.dgz48"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val keycloakVersion = "26.6.3"
val keycloakAdminClientVersion = "26.0.10"
val testcontainersVersion = "2.0.5"
val mysqlConnectorVersion = "9.1.0"

// SPI と MySQL JDBC ドライバの両 JAR を Keycloak Testcontainers の providers/ へ配備する。
// driver の JAR パスは下記 test タスクでシステムプロパティとして渡す(MySQLContainer 起動後に
// Keycloak コンテナへ withProviderLibsFrom で投入する)。
// 非 transitive: classic JDBC では connector-j 単体で完結する(protobuf 等の optional 依存は不要)。
val mysqlDriver: Configuration by configurations.creating { isTransitive = false }

dependencies {
    compileOnly("org.keycloak:keycloak-core:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    // User Storage SPI の基底(UserStorageProvider / UserStorageProviderFactory)は model-storage に在る。
    compileOnly("org.keycloak:keycloak-model-storage:$keycloakVersion")
    implementation("org.jspecify:jspecify:1.0.0")

    mysqlDriver("com.mysql:mysql-connector-j:$mysqlConnectorVersion")

    testImplementation("org.keycloak:keycloak-core:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-server-spi:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-model-storage:$keycloakVersion")
    // component テストで AbstractInMemoryUserAdapter を JVM 内生成する際の推移依存(JTA API)。
    testImplementation("jakarta.transaction:jakarta.transaction-api:2.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers(MySQL 8.4 + Keycloak 26.x)。ADR-0006 §6 の検証方法に従い実 DB / 実 Keycloak で検証する。
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("com.github.dasniko:testcontainers-keycloak:4.2.1")

    // Keycloak Admin REST API クライアント(SPI 経由の read/write/create/delete を検証)。
    testImplementation("org.keycloak:keycloak-admin-client:$keycloakAdminClientVersion")

    // テスト側から users テーブルへ直接 seed / 検証するための JDBC ドライバ。
    testImplementation("com.mysql:mysql-connector-j:$mysqlConnectorVersion")

    // E2E(Account / Admin Console のブラウザ操作 + Update-Email 到達検証)。
    testImplementation("com.microsoft.playwright:playwright:1.60.0")

    testImplementation("org.assertj:assertj-core:3.27.7")
    // component テスト(SPI クラスを JVM 内で直接駆動 → JaCoCo 計測対象)で Keycloak SPI 型を mock する。
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.18")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.13.7")
}

tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        error("NullAway")
        option("NullAway:AnnotatedPackages", "xyz.dgz48")
        option("NullAway:JSpecifyMode", "true")
        error("BadImport")
        error("JavaTimeDefaultTimeZone")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        disable("NullAway")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Keycloak Testcontainers は providers/ に「ビルド済み SPI JAR」+「MySQL JDBC ドライバ」を必要とする。
    // jar タスクの成果物と mysqlDriver 構成の解決結果をシステムプロパティで渡し、テスト側で配備する。
    dependsOn(tasks.named("jar"))
    val spiJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    doFirst {
        systemProperty("tasks.spi.jar", spiJar.get().asFile.absolutePath)
        systemProperty("tasks.mysql.driver.jar", mysqlDriver.singleFile.absolutePath)
    }
    // Testcontainers のコンテナ起動・Keycloak 初回ブートは時間がかかるため余裕を持たせる。
    systemProperty("junit.jupiter.execution.timeout.default", "10m")
    // Playwright は既定で chromium/firefox/webkit を全 DL する。E2E は chromium のみ使うため限定する。
    environment("PLAYWRIGHT_BROWSERS_TO_INSTALL", "chromium")
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// webapi と同一基準: 命令(INSTRUCTION)カバレッジ 80%。JaCoCo の rule limit は既定で
// counter=INSTRUCTION / value=COVEREDRATIO のため、webapi 同様 counter は明示しない。
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
        googleJavaFormat("1.24.0")
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", "jacocoTestCoverageVerification")
}
