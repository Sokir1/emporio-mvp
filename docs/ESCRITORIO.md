# Compilación y estructura de escritorio

El directorio `Escritorio` conserva el código y las bibliotecas del ZIP final. La aplicación usa Windows Forms y WebView2 para presentar la misma web del backend.

## Compilar

Requisitos: Windows de 64 bits, .NET Framework con compilador C# y WebView2 Runtime instalado para ejecutar la interfaz. Las bibliotecas necesarias para compilar están incluidas con sus licencias originales.

Desde la raíz del repositorio:

```powershell
./Escritorio/Compilar.ps1
```

Los resultados se escriben en `Escritorio/bin`:

- `Emporio.exe`: abridor de la aplicación.
- `Abrir pagina web.exe`: abridor de la web.
- `EmporioApp.exe` y su configuración: interfaz Windows.
- `escritorio`: bibliotecas WebView2.

## Estructura del paquete portable

```text
Emporio/
  Emporio.exe
  Abrir pagina web.exe
  Sistema/
    EmporioApp.exe
    EmporioApp.exe.config
    Emporio.jar
    Emporio.ps1
    escritorio/
    runtime/
      java/bin/java.exe
      mariadb/bin/...
```

El JAR se obtiene al compilar `Backend`. `Escritorio/Emporio.ps1` gestiona el arranque, la parada y los respaldos. La instalación genera su configuración y credenciales locales; esos archivos no se publican.

Este repositorio publica el código de la versión final. No redistribuye el paquete privado de entrega ni los datos de una instalación. La comprobación del backend y las pruebas de inventario no sustituyen una prueba del paquete Windows completo.
