$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
$dependencyRoot=Join-Path $projectRoot 'dependencias\webview2'
$outputRoot=Join-Path $projectRoot 'bin'
$libraries=Join-Path $outputRoot 'escritorio'
New-Item -ItemType Directory -Path $libraries -Force | Out-Null
$frameworkRoot=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$core=Join-Path $dependencyRoot 'lib\net462\Microsoft.Web.WebView2.Core.dll'
$forms=Join-Path $dependencyRoot 'lib\net462\Microsoft.Web.WebView2.WinForms.dll'

Add-Type -AssemblyName System.Drawing
$bitmap=New-Object System.Drawing.Bitmap 128,128
$graphics=[System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(209,231,183))
$pen=New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(37,72,53)),7
$pen.StartCap=[System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap=[System.Drawing.Drawing2D.LineCap]::Round
$leaf=New-Object System.Drawing.Drawing2D.GraphicsPath
$leaf.AddBezier(33,81,25,45,61,31,96,31)
$leaf.AddBezier(96,31,96,67,78,102,47,91)
$graphics.DrawPath($pen,$leaf)
$graphics.DrawLine($pen,32,101,81,49)
$graphics.DrawLine($pen,57,73,57,52)
$graphics.DrawLine($pen,57,73,80,73)
$stream=New-Object System.IO.MemoryStream
$bitmap.Save($stream,[System.Drawing.Imaging.ImageFormat]::Png)
$png=$stream.ToArray()
$iconFile=Join-Path $outputRoot 'Emporio.ico'
$iconStream=[System.IO.File]::Create($iconFile)
$writer=New-Object System.IO.BinaryWriter $iconStream
$writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]1)
$writer.Write([byte]128); $writer.Write([byte]128); $writer.Write([byte]0); $writer.Write([byte]0)
$writer.Write([uint16]1); $writer.Write([uint16]32); $writer.Write([uint32]$png.Length); $writer.Write([uint32]22)
$writer.Write($png)
$writer.Dispose(); $stream.Dispose(); $graphics.Dispose(); $bitmap.Dispose(); $pen.Dispose(); $leaf.Dispose()

$compiler=Join-Path $frameworkRoot 'csc.exe'
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /warnaserror+ /codepage:65001 "/out:$outputRoot\EmporioApp.exe" "/win32icon:$iconFile" "/win32manifest:$projectRoot\Emporio.manifest" "/reference:$frameworkRoot\System.Windows.Forms.dll" "/reference:$frameworkRoot\System.Drawing.dll" "/reference:$frameworkRoot\System.Web.Extensions.dll" "/reference:$core" "/reference:$forms" "$projectRoot\Emporio.cs"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar Emporio.exe.' }
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /warnaserror+ /codepage:65001 "/out:$outputRoot\Emporio.exe" "/win32icon:$iconFile" "/win32manifest:$projectRoot\Emporio.manifest" "/reference:$frameworkRoot\System.Windows.Forms.dll" "/reference:$frameworkRoot\System.Drawing.dll" "$projectRoot\Lanzador.cs"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el abridor de la aplicación.' }
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /warnaserror+ /codepage:65001 /define:WEB "/out:$outputRoot\Abrir pagina web.exe" "/win32icon:$iconFile" "/win32manifest:$projectRoot\Emporio.manifest" "/reference:$frameworkRoot\System.Windows.Forms.dll" "/reference:$frameworkRoot\System.Drawing.dll" "$projectRoot\Lanzador.cs"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el abridor de la página web.' }
Copy-Item -LiteralPath "$projectRoot\Emporio.exe.config" -Destination "$outputRoot\EmporioApp.exe.config" -Force
Copy-Item -LiteralPath $core,$forms -Destination $libraries -Force
Copy-Item -LiteralPath "$dependencyRoot\runtimes\win-x64\native\WebView2Loader.dll" -Destination $libraries -Force
Copy-Item -LiteralPath "$dependencyRoot\LICENSE.txt" -Destination "$libraries\LICENCIA-WebView2.txt" -Force
Copy-Item -LiteralPath "$dependencyRoot\NOTICE.txt" -Destination "$libraries\AVISOS-WebView2.txt" -Force
Write-Output "Compilado: $outputRoot\Emporio.exe"
