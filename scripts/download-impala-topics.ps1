$ErrorActionPreference = "Stop"

$base = "https://impala.apache.org/docs/build/plain-html/"
$indexPath = "D:\hadoop-guardian\tmp_impala_plain.html"
$target = "D:\hadoop-guardian\backend\src\main\resources\knowledge-docs\impala-topics"

$html = Get-Content $indexPath -Raw
$matches = [regex]::Matches($html, 'href="([^"]+\.html)"')
$links = New-Object System.Collections.Generic.List[string]

foreach ($match in $matches) {
    $url = $match.Groups[1].Value
    if ($url -like "topics/*.html" -and -not $links.Contains($url)) {
        $links.Add($url)
    }
}

New-Item -ItemType Directory -Force $target | Out-Null

foreach ($link in $links) {
    $name = Split-Path $link -Leaf
    $out = Join-Path $target $name
    curl.exe -L ($base + $link) -o $out | Out-Null
}

Write-Output ("downloaded=" + $links.Count)
