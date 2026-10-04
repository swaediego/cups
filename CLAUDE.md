# cups (APK Android)

## Flujo de ramas
- Todo el desarrollo y las pruebas van en la rama **`prueba`** (siempre la misma). No hagas commit en `main`.
- `main` solo se actualiza cuando el usuario dice **"publícalo"**: se usa la skill `publicar`
  (merge `prueba` → `main`, `publicar.ps1`, Release en GitHub).
- Si no estás en `prueba` al empezar a trabajar, haz `git checkout prueba`.
- La web equivalente es `C:\Dev\cups-web` (misma regla). Ambas deben verse y comportarse igual.

## Publicar
- Las actualizaciones llegan a los usuarios por GitHub Releases (tag `vX.Y` = `versionName`, APK adjunto).
- Compilar no publica nada; solo `publicar.ps1` (vía la skill) lo hace.
- El APK se firma con `keystore/cups.keystore` (fuera de git; no la regeneres ni la pierdas).
