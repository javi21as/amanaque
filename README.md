# Mi almanaque

Aplicación Android personal para registrar días trabajados, días de descanso, ganancias y gastos de cada jornada. El resumen del calendario muestra por separado las ganancias y los gastos del mes. Al abrir un día también se pueden guardar recordatorios asociados a esa fecha; aparecen marcados discretamente en el calendario y se pueden mostrar u ocultar con el botón **Ver recordatorios**. Los datos se guardan en el dispositivo. Desde el menú **Mi espacio** se pueden crear objetivos personales semanales, mensuales o anuales, y metas de ganancias mensuales o anuales cuyo progreso se calcula a partir de los días trabajados registrados.

La app incluye un recordatorio diario para las 10:00 p. m. Pulsa **Activar** en la pantalla del calendario y permite las notificaciones. Para que Android lo entregue a la hora exacta, permite también las alarmas exactas en la configuración del sistema; sin ese permiso, Android puede retrasar el aviso.

## Generar el APK

1. Instala Android Studio y, desde el SDK Manager, instala Android SDK Platform 35.
2. Abre en Android Studio la carpeta que contiene este README y espera a que termine la sincronización de Gradle.
3. Para probar la app, conecta un teléfono con la depuración USB activada y pulsa **Run**.
4. Para crear un APK de prueba, selecciona **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Android Studio mostrará la ubicación del APK generado.
5. Para distribuir la app, selecciona **Build > Generate Signed Bundle / APK** y sigue el asistente para firmar el APK.

La app no necesita conexión a Internet. Los datos se guardan localmente en el teléfono y no se sincronizan con otros dispositivos.
