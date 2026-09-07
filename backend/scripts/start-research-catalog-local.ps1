param(
    [int] $Port = 8080,
    [string] $CatalogPath = "",
    [string] $BasicUser = "research-local",
    [switch] $PrintCommandOnly
)

$ErrorActionPreference = "Stop"

$backendRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$repoRoot = Resolve-Path -LiteralPath (Join-Path $backendRoot "..")

if ([string]::IsNullOrWhiteSpace($CatalogPath)) {
    $CatalogPath = Join-Path $repoRoot "data-pipeline/data/processed/junyi_catalog_v2.json"
}
$CatalogPath = [System.IO.Path]::GetFullPath($CatalogPath)

if (-not (Test-Path -LiteralPath $CatalogPath)) {
    throw "Research catalog file is missing: $CatalogPath. Regenerate schema v2 with data-pipeline/scripts/export_junyi_catalog.py."
}
if ([string]::IsNullOrWhiteSpace($env:EDU_RESEARCH_BASIC_PASSWORD)) {
    throw "Set EDU_RESEARCH_BASIC_PASSWORD in the current shell before starting. The script does not store or print it."
}

$javaExe = $null
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $candidate = Join-Path $env:JAVA_HOME "bin/java.exe"
    if (Test-Path -LiteralPath $candidate) {
        $javaExe = $candidate
    }
}
if ($null -eq $javaExe) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        throw "Java was not found. Configure JAVA_HOME or place Java 21 on PATH."
    }
    $javaExe = $javaCommand.Source
}

$jar = Get-ChildItem -LiteralPath (Join-Path $backendRoot "target") -Filter "edu-backend-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw "Packaged backend jar was not found. Run 'mvn --batch-mode package' in backend/ first."
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
    "-jar",
    $jar.FullName,
    "--server.address=127.0.0.1",
    "--server.port=$Port",
    "--spring.profiles.active=local-research",
    "--app.research.exercises.catalog-path=$CatalogPath",
    "--spring.autoconfigure.exclude=$($excludedAutoConfigurations -join ",")"
)

if ($PrintCommandOnly) {
    Write-Output "java=$javaExe"
    Write-Output "jar=$($jar.FullName)"
    Write-Output "bind=127.0.0.1:$Port"
    Write-Output "catalog_path=$CatalogPath"
    Write-Output "basic_user=$BasicUser"
    Write-Output "basic_password_source=EDU_RESEARCH_BASIC_PASSWORD"
    Write-Output "excluded_auto_configurations=$($excludedAutoConfigurations.Count)"
    return
}

& $javaExe @arguments
