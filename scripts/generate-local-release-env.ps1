[CmdletBinding()]
param(
    [string]$Path = '.env.release',
    [switch]$Force
)

$resolvedPath = [System.IO.Path]::GetFullPath($Path)
if ((Test-Path -LiteralPath $resolvedPath) -and -not $Force) {
    throw "Refusing to overwrite existing release environment file: $resolvedPath. Pass -Force only when replacement is intended."
}

function New-PrivateValue {
    param([int]$ByteCount = 32)
    $bytes = New-Object byte[] $ByteCount
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return 'local_' + [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$content = @"
MYSQL_DATABASE=edu
MYSQL_USER=edu
MYSQL_PASSWORD=$(New-PrivateValue 24)
MYSQL_ROOT_PASSWORD=$(New-PrivateValue 24)
MYSQL_PORT=3306
JWT_SECRET=$(New-PrivateValue 48)
JWT_ISSUER=https://edu-java.local
JWT_ACCESS_TOKEN_TTL_SECONDS=3600
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=$(New-PrivateValue 24)
NEO4J_HTTP_PORT=7474
NEO4J_BOLT_PORT=7687
REDIS_PORT=6379
BACKEND_PORT=8080
FRONTEND_PORT=8081
VITE_API_BASE_URL=/api/v1
CORS_ALLOWED_ORIGINS=http://localhost:8081
APP_BUILD_VERSION=0.7.0-rc-local
APP_BUILD_TIME=local
APP_DEMO_SEED_ENABLED=true
"@

[System.IO.File]::WriteAllText(
    $resolvedPath,
    $content.Trim() + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "Created local release environment file: $resolvedPath"
