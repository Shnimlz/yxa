# ⚡ Yxa - Android Kernel & Performance Suite

![Kotlin](https://img.shields.io/badge/Kotlin-B125EA?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Root Required](https://img.shields.io/badge/Root-Required-red?style=for-the-badge&logo=magisk&logoColor=white)
![Status](https://img.shields.io/badge/Status-Active_Development-success?style=for-the-badge)

**Yxa** ha evolucionado de un simple panel de control a una suite de ingeniería de sistemas de muy bajo nivel para Android. Diseñada para usuarios avanzados y gamers competitivos, Yxa inyecta comandos directamente en el núcleo del sistema para erradicar el *lag*, optimizar la carga y exprimir al máximo el hardware del dispositivo.

⚠️ **ADVERTENCIA DE SEGURIDAD:** Esta aplicación manipula parámetros críticos del Kernel y *sysfs*. Requiere acceso **Root** absoluto. El uso indebido de las opciones de CPU y voltajes puede resultar en un *Bootloop* o daño térmico. Úsala bajo tu propio riesgo.

---

## 🛠️ Capacidades del Sistema

### ⚙️ 1. Motor Base
Yxa evita las APIs estándar y restrictivas de Android, operando directamente sobre el kernel:
* **Uso de Root:** Dependencia íntegra de permisos de Superusuario para inyectar comandos en las capas de hardware (`sysfs`) y del SO (`procfs`).
* **BusyBox Estático:** Empaqueta y despliega su propio binario estático de BusyBox en el almacenamiento interno, asegurando que comandos Unix robustos (`awk`, `grep`, `top`) funcionen en cualquier ROM.
* **Iperf3 Nativo:** Binario compilado para realizar pruebas asíncronas de estrés UDP y medir con exactitud militar la latencia y el *jitter*.
* **SQLite3:** Soporte binario embebido para la manipulación directa de bases de datos a bajo nivel.

### 🌐 2. Red y Conectividad
Módulo diseñado para destruir el lag y garantizar la supremacía de los paquetes de juego:
* **TCP Stack:** Modificación a nivel kernel de algoritmos de congestión (BBR, Cubic, Reno) para priorizar entrega rápida.
* **Control IPv6:** Deshabilitación global de la pila IPv6 para prevenir fugas DNS y colisiones de enrutamiento.
* **Inyección DNS:** Alteración de tablas de resolución para forzar servidores de baja latencia (Cloudflare, Google).
* **Auto-MTU (MtuOptimizer):** Algoritmo de búsqueda binaria con pruebas de *ping* en tiempo real (1200-1500 bytes) para erradicar la fragmentación de paquetes.
* **Gestor de Bufferbloat (fq_codel):** Inyección de la disciplina de encolamiento `fq_codel` en la interfaz Wi-Fi (`wlan0`) para proteger el ancho de banda.
* **LagProtectorService:** Servicio en primer plano (*Foreground*) que ejecuta pruebas Iperf3 cada 30s. Si detecta fluctuaciones (*jitter* > 30ms), bloquea temporalmente mediante `iptables` el acceso a internet de apps en segundo plano.

### 🧠 3. CPU (Gestión Térmica y Procesamiento)
Control absoluto a nivel de semiconductor:
* **Perfiles de Energía:** Ajustes automáticos de los gobernadores (*Performance*, *Schedutil*, *Powersave*).
* **Control por Cluster (big.LITTLE):** Bloqueo manual de frecuencias máximas y mínimas para cada núcleo individual.
* **CPUsets (top-app):** Reasignación de los núcleos más potentes exclusivamente al grupo `top-app` para forzar al sistema a dar el 100% de atención al juego en pantalla.
* **CPU Boost:** Inyección de picos de frecuencia máxima inmediata al detectar toques en pantalla (reducción de *input lag*).
* **Thermal Throttling:** Alteración de límites térmicos para retrasar la caída de FPS bajo estrés.

### 🎮 4. GPU (Renderizado y Aceleración)
Optimización gráfica sin compromisos:
* **Detección de Hardware:** Lectura directa de `sysfs` para identificar y monitorear cargas y frecuencias de GPUs (Adreno, Mali).
* **Forzado MSAA y 2D:** Inyección vía `build.prop` / `setprop` para forzar Anti-Aliasing (4x MSAA) y aceleración 2D constante.
* **Adreno Idler:** Ajuste de temporizadores de inactividad para evitar reducciones de velocidad entre fotogramas.
* **Tunables Avanzados:** Control granular sobre velocidades de reloj gráfico y consumo energético.

### 🗄️ 5. RAM (Máquina Virtual y Limpieza Inteligente)
Manipulación de la Máquina Virtual de Linux (VM):
* **Parámetros VM:** Modificación directa vía `sysctl` para alterar *Swappiness*, presión de caché (`vfs_cache_pressure`) y umbral de memoria libre (`min_free_kbytes`).
* **ZRAM y KSM:** Monitoreo y fusión de páginas de memoria idénticas para ahorrar RAM física.
* **AutoRamCleanerService:** Servicio persistente (*Foreground Ninja*) que fuerza limpieza de cachés y terminación segura de procesos si la RAM supera un umbral crítico (ej. 85%).
* **Sistema Whitelist (Lista Blanca):** Selector visual integrado para proteger aplicaciones críticas (Spotify, Discord, llamadas) de la guadaña del Auto-Limpiador.

### 💾 6. Almacenamiento
* **FSTRIM Nativo:** Ejecución asíncrona de recolección de basura para prevenir degradación de lectura/escritura en discos eMMC/UFS, con reportes precisos de tiempo.
* **I/O Scheduler:** Modificación del algoritmo de lectura del disco hacia modos de baja latencia (ej. `deadline`), acelerando la carga de mapas y texturas pesadas.

### 🕹️ 7. GameTime
Lanzador dedicado para experiencias extremas:
* **Perfiles por Juego:** Automatización de resolución, tasa de refresco (Hz) y perfil gráfico al lanzar un juego específico.
* **Overlay (Panel Flotante):** Telemetría en tiempo real sobre la pantalla (Uso CPU/GPU/RAM).
* **Gestión Extrema:** Botones de acción rápida pre-lanzamiento para liberar *inodes*, *dentries* y *pagecaches* inmediatamente.

### 🚀 8. Sistema y Arranque
Autonomía sin intervención manual:
* **BootCompletedReceiver:** Listener seguro que se acopla al despertar del SO para aplicar todas las optimizaciones guardadas silenciosamente.
* **Motor Init.d / Service.d:** Sistema inteligente (`BootScriptManager`) que empaqueta las configuraciones en un script bash blindado para ser ejecutado nativamente por Magisk al arranque del kernel.

---
*Desarrollado con pasión basandome en CachyOS.*