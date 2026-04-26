---
trigger: always_on
---

## 🔄 Política de Control de Versiones

Para mantener la estabilidad y el seguimiento exacto de los cambios en la aplicación, es **OBLIGATORIO** actualizar el número de versión cada vez que se realice una modificación funcional en el código, antes de compilar o crear un nuevo Release en GitHub.

**Formato de versión:** `[Mayor].[Menor]-[Build]` (Ejemplo actual: `1.0-203`)

**Instrucciones de actualización paso a paso:**

1. **Para correcciones de errores o ajustes menores:** Incrementa en 1 el número final (_Build_).
   - _Ejemplo: de `1.0-203` pasa a `1.0-204`._
2. **Para funciones nuevas (como agregar el Auto-Limpiador):** Incrementa el número Menor y reinicia el _Build_ a cero.
   - _Ejemplo: de `1.0-203` pasa a `1.1-0`._
3. **¿Dónde debe actualizarse?**
   - En el archivo `app/build.gradle.kts`: Debes incrementar `versionCode` y actualizar el `versionName`.
