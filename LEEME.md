# Mi Launcher (Android TV / Google TV)

## Generar el APK en GitHub

1. Descomprimí el .zip en tu computadora.
2. En GitHub creá un repositorio nuevo, preferentemente **privado** (incluye la clave de firma).
3. En el repositorio vacío elegí **uploading an existing file** y arrastrá el *contenido* de la carpeta
   `MiLauncher` (no el .zip ni la carpeta contenedora). Confirmá con **Commit changes**.
4. La carpeta `.github` suele estar oculta. Si no se subió: **Add file > Create new file**, escribí como
   nombre `.github/workflows/compilar-apk.yml`, pegá el contenido de ese archivo y confirmá.
5. Abrí la pestaña **Actions** y esperá a que "Compilar APK" termine en verde. Si no arrancó, elegilo y
   tocá **Run workflow**.
6. Entrá en esa ejecución y, abajo, en **Artifacts**, descargá `MiLauncher-apk`. Es un .zip que contiene
   `MiLauncher.apk`.

## Instalar en la TV

Copiá el APK a un pendrive o envialo a la TV, y abrilo con un administrador de archivos (hay que permitir
la instalación de apps desconocidas).

## Uso

- **+ Agregar apps** (en el inicio): marcar las favoritas.
- Mantener presionado **OK** sobre una app: favoritas, mover, ocultar o ver su información.
- Botón de controles deslizantes (arriba): fondo, tema, tamaño de íconos, reloj, apps ocultas y launcher predeterminado.
- **Inicio** estando en el launcher lleva a la barra superior (requiere que sea el predeterminado).
- El tema "Según el sistema" usa el modo oscuro si la TV no informa ninguno.

## Si el botón Inicio abre el launcher original

En Mi Launcher: ajustes del launcher > **Botón Inicio abre Mi Launcher** > Abrir Accesibilidad, y activá
**Mi Launcher: botón Inicio**.

Si la opción aparece bloqueada ("ajuste restringido"), con la depuración activada:

    adb connect IP_DE_LA_TV
    adb shell appops set com.santi.launcher ACCESS_RESTRICTED_SETTINGS allow

y volvé a intentar. Algunas TV apagan los servicios de accesibilidad al reiniciar o actualizar; en ese caso
hay que activarlo de nuevo.

## Alternativa: desactivar el launcher original

Google TV suele no permitir elegir otro launcher; se puede desactivar el original por ADB:

    adb connect IP_DE_LA_TV
    adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx

Para revertirlo:

    adb shell pm enable com.google.android.apps.tv.launcherx

En Android TV (no Google TV) el paquete es `com.google.android.tvlauncher`. Hacelo bajo tu responsabilidad:
en algunos equipos desactivarlo afecta funciones del sistema o no está permitido.
