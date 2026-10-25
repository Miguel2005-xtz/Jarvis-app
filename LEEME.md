# App de Jarvis para Android

Es una "cáscara" que se conecta a Jarvis en tu PC: muestra la misma pantalla y usa el micrófono de Android para tus órdenes. El PC debe estar encendido con `iniciar_jarvis_celular.bat`.

## Convertirla en APK sin instalar nada (GitHub, gratis)
1. Crea una cuenta en github.com y pulsa **New repository** (ponle de nombre `jarvis-app`, déjalo **Private**).
2. Extrae este ZIP en tu PC. Dentro del repositorio pulsa **Add file > Upload files** y arrastra TODO el contenido de la carpeta extraída (incluida la carpeta oculta `.github`). Pulsa **Commit changes**.
3. Ve a la pestaña **Actions**, elige **Construir APK de Jarvis** y pulsa **Run workflow**. Tarda unos 5 a 10 minutos.
4. Al terminar (marca verde), entra en esa ejecución y descarga **Jarvis-APK** (abajo, en Artifacts). Es un ZIP con `app-debug.apk`.
5. Pasa el APK al celular, ábrelo y permite "instalar apps desconocidas" cuando Android lo pida.

## Primer uso
Al abrir la app escribe la dirección que muestra la ventana de Jarvis (por ejemplo `http://192.168.1.25:8765`) y tu PIN. Acepta el permiso de micrófono. El engranaje ⚙ (arriba a la izquierda) cambia la dirección o el PIN.
