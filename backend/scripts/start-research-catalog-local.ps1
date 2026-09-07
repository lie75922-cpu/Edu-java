param(
    [int] $Port = 8080,
    [string] $CatalogPath = "D:/Code/java/data-pipeline/data/processed/junyi_catalog_v1.json",
    [string] $BasicUser = "research-local",
    [switch] $PrintCommandOnly
)

$ErrorActionPreference = "Stop"

$backendRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$surefireXml = Join-Path $backendRoot "target/surefire-reports/TEST-com.smartlearning.research.ResearchExerciseControllerTest.xml"
$mainClasses = Join-Path $backendRoot "target/classes"
$mainClass = Join-Path $mainClasses "com/smartlearning/EduApplication.class"
$javaExe = "D:/software/Java/jdk-21.0.12/bin/java.exe"

if (-not (Test-Path -LiteralPath $surefireXml)) {
    throw "Surefire XML classpath source is missing. Run backend tests once before local direct-java startup."
}
if (-not (Test-Path -LiteralPath $mainClass)) {
    throw "Compiled backend classes are missing. Compile backend before local direct-java startup."
}
if (-not (Test-Path -LiteralPath $CatalogPath)) {
    throw "Research catalog file is missing: $CatalogPath"
}
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "Java executable is missing: $javaExe"
}
if ([string]::IsNullOrWhiteSpace($env:EDU_RESEARCH_BASIC_PASSWORD)) {
    throw "Set EDU_RESEARCH_BASIC_PASSWORD in the current shell before starting. The script does not store or print it."
}

[xml] $report = Get-Content -LiteralPath $surefireXml
$classpathProperty = $report.testsuite.properties.property | Where-Object { $_.name -eq "java.class.path" } | Select-Object -First 1
if (-not $classpathProperty) {
    throw "java.class.path property was not found in Surefire XML."
}

$excludedEntries = @(
    (Join-Path $backendRoot "target/test-classes"),
    (Join-Path $backendRoot "target/surefire"),
    (Join-Path $backendRoot "target/generated-test-sources")
) | ForEach-Object { [System.IO.Path]::GetFullPath($_) }

$classpathEntries = @()
foreach ($entry in ($classpathProperty.value -split ";")) {
    if ([string]::IsNullOrWhiteSpace($entry)) {
        continue
    }
    $full = [System.IO.Path]::GetFullPath($entry)
    if ($excludedEntries -contains $full) {
        continue
    }
    $classpathEntries += $full
}

if (-not ($classpathEntries -contains ([System.IO.Path]::GetFullPath($mainClasses)))) {
    throw "Runtime classpath does not include target/classes."
}

$env:SPRING_SECURITY_USER_NAME = $BasicUser
$env:SPRING_SECURITY_USER_PASSWORD = $env:EDU_RESEARCH_BASIC_PASSWORD

$excludedAutoConfigurations = @(
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
    "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
    "org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jAutoConfiguration",
    "org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jRepositoriesAutoConfiguration"
)

$arguments = @(
    "-cp",
    ($classpathEntries -join ";"),
    "com.smartlearning.EduApplication",
    "--server.address=127.0.0.1",
    "--server.port=$Port",
    "--spring.profiles.active=local-research",
    "--app.research.exercises.catalog-path=$CatalogPath",
    "--spring.autoconfigure.exclude=$($excludedAutoConfigurations -join ",")"
)

if ($PrintCommandOnly) {
    Write-Output "java=$javaExe"
    Write-Output "main_class=com.smartlearning.EduApplication"
    Write-Output "classpath_entries=$($classpathEntries.Count)"
    Write-Output "bind=127.0.0.1:$Port"
    Write-Output "catalog_path=$CatalogPath"
    Write-Output "basic_user=$BasicUser"
    Write-Output "basic_password_source=EDU_RESEARCH_BASIC_PASSWORD"
    Write-Output "excluded_auto_configurations=$($excludedAutoConfigurations.Count)"
    return
}

& $javaExe @arguments
